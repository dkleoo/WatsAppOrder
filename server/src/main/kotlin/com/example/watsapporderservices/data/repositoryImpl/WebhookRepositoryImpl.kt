package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.enum.SessionState
import com.example.watsapporderservices.data.mapper.ProductResponse
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.AiRepository
import com.example.watsapporderservices.domain.repository.MessageOption
import com.example.watsapporderservices.domain.repository.MessageRepository
import com.example.watsapporderservices.domain.repository.OrderRepository
import com.example.watsapporderservices.domain.repository.ProductRepository
import com.example.watsapporderservices.domain.repository.SessionRepository
import com.example.watsapporderservices.domain.repository.StoreRepository
import com.example.watsapporderservices.domain.repository.WebhookRepository
import com.example.watsapporderservices.domain.usecase.AiResult
import com.example.watsapporderservices.domain.usecase.MessageResult
import com.example.watsapporderservices.domain.usecase.OrderDraft
import com.example.watsapporderservices.domain.usecase.SessionInfo
import com.example.watsapporderservices.domain.usecase.WebhookResult
import com.example.watsapporderservices.domain.usecase.WebhookStatus
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val SUBSCRIBE_MODE = "subscribe"
private const val MESSAGES_FIELD = "messages"
private const val TEXT_MESSAGE_TYPE = "text"
private const val INTERACTIVE_MESSAGE_TYPE = "interactive"
private const val MAX_REPLY_LENGTH = 4096
private const val MAX_TRACKED_MESSAGE_IDS = 1000
private const val PAGE_SIZE = 10
private const val ASK_WHAT_TO_ORDER = "¿Qué deseas ordenar?"
private const val QUESTION_SYSTEM_PROMPT =
    "Eres el asistente de pedidos de una tienda por WhatsApp. Devuelve SOLO una pregunta breve y clara, " +
        "sin listar productos y sin inventar datos."

// Interactive ids: "product:<id>", "more:<page>", "confirm:yes", "confirm:no".
private const val PRODUCT_ID_PREFIX = "product:"
private const val MORE_ID_PREFIX = "more:"
private const val CONFIRM_YES_ID = "confirm:yes"
private const val CONFIRM_NO_ID = "confirm:no"

private val STORE_INFO_KEYWORDS = listOf(
    "direccion", "dirección", "donde", "dónde", "ubicacion", "ubicación",
    "telefono", "teléfono", "contacto", "whatsapp",
)

private val GREETING_WORDS = listOf(
    "hola", "buenas", "buenos dias", "buenos días", "buenas tardes", "buenas noches",
    "hey", "hi", "hello", "saludos", "que tal", "qué tal",
)

private val CATALOG_KEYWORDS = listOf(
    "mi catalogo", "mi catálogo", "catalogo", "catálogo", "menu", "menú",
    "que venden", "que vendes", "que tienen", "que tienes", "que hay",
)

private val AFFIRMATIVE_WORDS = listOf("si", "sí", "sii", "claro", "ok", "okay", "dale", "yes", "porfa", "por favor")
private val NEGATIVE_WORDS =
    listOf("no", "nop", "nope", "gracias", "listo", "eso es todo", "nada mas", "nada más")

class WebhookRepositoryImpl(
    private val config: WhatsAppConfig,
    private val aiRepository: AiRepository,
    private val messageRepository: MessageRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val sessionRepository: SessionRepository,
    private val orderRepository: OrderRepository,
) : WebhookRepository {
    private val logger = LoggerFactory.getLogger(WebhookRepositoryImpl::class.java)
    private val processedMessageIds = ConcurrentHashMap.newKeySet<String>()

    private val receivedEvents = AtomicLong()
    private val receivedMessages = AtomicLong()
    private val replied = AtomicLong()
    private val failed = AtomicLong()
    private val lastActivity = AtomicLong()
    private val lastError = AtomicReference<String?>(null)

    override suspend fun verify(mode: String?, token: String?, challenge: String?): WebhookResult {
        if (mode != SUBSCRIBE_MODE) return WebhookResult.Rejected
        if (challenge.isNullOrEmpty()) return WebhookResult.Rejected
        if (!config.isConfigured || token == null) return WebhookResult.Rejected
        if (!constantTimeEquals(token, config.verifyToken)) return WebhookResult.Rejected
        return WebhookResult.Verified(challenge)
    }

    override fun status(): WebhookStatus = WebhookStatus(
        receivedEvents = receivedEvents.get(),
        receivedMessages = receivedMessages.get(),
        replied = replied.get(),
        failed = failed.get(),
        lastActivityEpochMs = lastActivity.get().takeIf { it > 0 },
        lastError = lastError.get(),
    )

    override suspend fun handleEvent(payload: WhatsAppWebhookPayload) {
        receivedEvents.incrementAndGet()
        lastActivity.set(System.currentTimeMillis())

        val business = extractBusiness(payload)
        val store = storeRepository.findByWhatsapp(business.phone, business.idWhatsApp)
        if (store == null) {
            logger.warn("No store configured for whatsapp {} / {}", business.phone, business.idWhatsApp)
        }
        val messages = extractMessages(payload)
        receivedMessages.addAndGet(messages.size.toLong())
        logger.info("Webhook event received: {} message(s)", messages.size)

        messages.forEach { incoming ->
            if (!markProcessed(incoming.id)) return@forEach

            val session = sessionRepository.resume(incoming.from, store?.id)
            val welcome = if (session.isNew && store != null) "${initialGreeting(store)}\n\n" else ""
            deliver(incoming.from, welcome, store, business, session, incoming)
        }
    }

    /**
     * Resolves the conversation for one incoming message and sends the reply. Text replies and interactive
     * menus go through [deliver], so the greeting can be prepended without losing the interactive type.
     */
    private suspend fun deliver(
        from: String,
        welcome: String,
        store: StoreResponse?,
        business: BusinessNumbers,
        session: SessionInfo,
        incoming: IncomingMessage,
    ) {
        val text = incoming.text
        val interactiveId = incoming.interactiveId

        // "mi catálogo" / greeting / store info are text-only intents handled before menus.
        if (session.state == SessionState.IDLE || session.state == SessionState.CONFIRMING_MORE) {
            val effective = text
            if (effective != null && asksForCatalog(effective)) {
                sendProductList(from, welcome, business, session, pageFor(effective))
                return
            }
            if (session.state == SessionState.IDLE && effective != null && !session.isNew &&
                store != null && asksForStoreInfo(effective)
            ) {
                sendText(from, welcome + storeInfo(store))
                return
            }
            if (session.state == SessionState.IDLE && effective != null && isGreeting(effective)) {
                sendText(from, welcome + ASK_WHAT_TO_ORDER)
                return
            }
        }
        val reply = buildReply(store, business, session, text, interactiveId)
        when (reply) {
            is Reply.Text -> sendText(from, welcome + reply.text)
            is Reply.List -> sendListReply(from, welcome, reply)
            is Reply.Buttons -> sendButtons(from, welcome, reply)
        }
    }

    private suspend fun buildReply(
        store: StoreResponse?,
        business: BusinessNumbers,
        session: SessionInfo,
        text: String?,
        interactiveId: String?,
    ): Reply = when (session.state) {
        SessionState.IDLE -> handleIdle(business, session, text)
        SessionState.SELECTING_PRODUCT -> handleProductSelection(session, text, interactiveId)
        SessionState.CONFIRMING_MORE -> handleMoreProducts(business, session, text, interactiveId)
        SessionState.SELECTING_INGREDIENTS -> handleIngredients(session, text, interactiveId)
        SessionState.AWAITING_QUANTITY -> handleQuantity(session, text)
        SessionState.AWAITING_NAME -> handleName(session, text)
        SessionState.AWAITING_ADDRESS -> handleAddress(session, text)
        SessionState.AWAITING_PAYMENT -> handlePayment(session, text)
    }

    private suspend fun handleIdle(business: BusinessNumbers, session: SessionInfo, text: String?): Reply {
        if (text == null) return Reply.Text(ASK_WHAT_TO_ORDER)
        val products = productRepository.searchByStore(business.phone, business.idWhatsApp, text)
        return when {
            products.isNotEmpty() -> productListOrStart(business, session, products, page = 0, header = null)
            else -> menuAfterNoMatch(business, session, text)
        }
    }

    private suspend fun menuAfterNoMatch(
        business: BusinessNumbers,
        session: SessionInfo,
        text: String,
    ): Reply {
        val menu = productRepository.searchByStore(business.phone, business.idWhatsApp, null)
        if (menu.isEmpty()) return Reply.Text("No tengo productos disponibles por ahora.")
        return productListOrStart(business, session, menu, page = 0, header = "No tengo \"$text\" en el menú.")
    }

    private suspend fun handleProductSelection(
        session: SessionInfo,
        text: String?,
        interactiveId: String?,
    ): Reply {
        val productId = productIdFrom(interactiveId)
            ?: text?.trim()?.toIntOrNull()?.let { session.optionProductIds.getOrNull(it - 1) }
            ?: return Reply.Text("Elige una opción de la lista.")
        val product = productRepository.getProduct(productId)
            ?: return Reply.Text("No pude encontrar esa opción, intenta de nuevo.")
        return startProduct(session, product)
    }

    private suspend fun handleMoreProducts(
        business: BusinessNumbers,
        session: SessionInfo,
        text: String?,
        interactiveId: String?,
    ): Reply {
        when (interactiveId) {
            CONFIRM_YES_ID -> return Reply.Text(ASK_WHAT_TO_ORDER)
            CONFIRM_NO_ID -> return askDeliveryDetails(session)
        }
        if (text == null) return moreButtons()
        val normalized = text.lowercase().trim()
        return when {
            isNegative(normalized) -> askDeliveryDetails(session)
            isAffirmative(normalized) -> Reply.Text(ASK_WHAT_TO_ORDER)
            else -> {
                val products = productRepository.searchByStore(business.phone, business.idWhatsApp, text)
                if (products.isNotEmpty()) productListOrStart(business, session, products, 0, null) else moreButtons()
            }
        }
    }

    private suspend fun handleIngredients(
        session: SessionInfo,
        text: String?,
        interactiveId: String?,
    ): Reply {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val chosen = if (interactiveId != null) {
            productIdFrom(interactiveId)?.let { listOf(it) }.orEmpty()
        } else {
            text?.let { parseNumbers(it) }?.mapNotNull { n -> session.optionProductIds.getOrNull(n - 1) }.orEmpty()
        }
        if (chosen.isEmpty()) return Reply.Text("Elige los ingredientes de la lista.")
        orderRepository.save(order.copy(selectedInputIds = chosen.distinct()))
        saveState(session, SessionState.AWAITING_QUANTITY, emptyList(), order.id)
        return Reply.Text(ask("pide la cantidad que desea", "¿Cuántos deseas?"))
    }

    private suspend fun handleQuantity(session: SessionInfo, text: String?): Reply {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val quantity = text?.let { parseNumbers(it).firstOrNull() }
        if (quantity == null) return Reply.Text("Indícame un número. ¿Cuántos deseas?")
        if (quantity <= 0) return Reply.Text("La cantidad debe ser mayor a 0. ¿Cuántos deseas?")
        orderRepository.save(order.copy(quantity = quantity))
        saveState(session, SessionState.CONFIRMING_MORE, emptyList(), order.id)
        return moreButtons()
    }

    private suspend fun handleName(session: SessionInfo, text: String?): Reply {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val name = text?.trim().orEmpty().take(160)
        if (name.isBlank()) return Reply.Text("¿A nombre de quién es el pedido?")
        orderRepository.save(order.copy(customerName = name))
        saveState(session, SessionState.AWAITING_ADDRESS, emptyList(), order.id)
        return Reply.Text(ask("pide la dirección de entrega", "¿Cuál es la dirección de entrega?"))
    }

    private suspend fun handleAddress(session: SessionInfo, text: String?): Reply {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val address = text?.trim().orEmpty().take(300)
        if (address.isBlank()) return Reply.Text("¿Cuál es la dirección de entrega?")
        orderRepository.save(order.copy(deliveryAddress = address))
        saveState(session, SessionState.AWAITING_PAYMENT, emptyList(), order.id)
        return Reply.Text(
            ask(
                "pide el tipo de pago (efectivo, transferencia, tarjeta, etc.)",
                "¿Cómo deseas pagar? (efectivo, transferencia, tarjeta...)",
            ),
        )
    }

    private suspend fun handlePayment(session: SessionInfo, text: String?): Reply {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val payment = text?.trim().orEmpty().take(60)
        if (payment.isBlank()) return Reply.Text("¿Cómo deseas pagar? (efectivo, transferencia, tarjeta...)")
        val total = (order.unitPrice ?: 0.0) * (order.quantity ?: 0)
        val placed = orderRepository.save(
            order.copy(paymentType = payment, total = total, status = OrderStatus.PLACED),
        )
        saveState(session, SessionState.IDLE, emptyList(), null)
        return Reply.Text(buildOrderSummary(placed))
    }

    private suspend fun startProduct(session: SessionInfo, product: ProductResponse): Reply {
        val created = orderRepository.createDraft(session.storeId, session.customerPhone)
        val order = orderRepository.save(
            created.copy(
                productId = product.id,
                productName = product.name,
                unitPrice = product.price,
                status = OrderStatus.DRAFT,
            ),
        )
        val inputs = product.steps.flatMap { it.inputs }
        if (inputs.isNotEmpty()) {
            saveState(session, SessionState.SELECTING_INGREDIENTS, inputs.map { it.id }, order.id)
            return Reply.List(
                body = "Elegiste ${product.name}. ¿Con qué ingredientes lo deseas?",
                buttonText = "Ingredientes",
                rows = inputs.map { MessageOption(id = "$PRODUCT_ID_PREFIX${it.id}", title = it.name) },
                paged = false,
            )
        }
        saveState(session, SessionState.AWAITING_QUANTITY, emptyList(), order.id)
        return Reply.Text(
            "Elegiste ${product.name} ($ ${product.price}).\n" +
                ask("pide la cantidad que desea", "¿Cuántos deseas?"),
        )
    }

    /**
     * Builds either a paged interactive list or, when there is a single match, starts the product directly.
     * The store's full product ids are saved on the session so selection resolution never depends on the label.
     */
    private suspend fun productListOrStart(
        business: BusinessNumbers,
        session: SessionInfo,
        products: List<ProductResponse>,
        page: Int,
        header: String?,
    ): Reply {
        if (products.size == 1 && header == null) return startProduct(session, products.single())

        val allIds = products.map { it.id }
        val start = page * PAGE_SIZE
        if (start !in products.indices) {
            val menu = productRepository.searchByStore(business.phone, business.idWhatsApp, null)
            return productListOrStart(business, session, menu, 0, null)
        }
        val pageItems = products.subList(start, minOf(start + PAGE_SIZE, products.size))
        val hasMore = start + PAGE_SIZE < products.size
        saveState(session, SessionState.SELECTING_PRODUCT, allIds)

        val rows = pageItems.map {
            MessageOption(id = "$PRODUCT_ID_PREFIX${it.id}", title = it.name, description = describe(it))
        }.toMutableList()
        if (hasMore) rows += MessageOption(id = "$MORE_ID_PREFIX${page + 1}", title = "Ver más productos")

        return Reply.List(
            body = header ?: "Estos son los productos disponibles:",
            buttonText = "Ver productos",
            rows = rows,
            paged = true,
        )
    }

    private fun moreButtons(): Reply = Reply.Buttons(
        body = "¿Deseas agregar algo más?",
        buttons = listOf(
            MessageOption(id = CONFIRM_YES_ID, title = "Sí"),
            MessageOption(id = CONFIRM_NO_ID, title = "No"),
        ),
    )

    private suspend fun askDeliveryDetails(session: SessionInfo): Reply {
        saveState(session, SessionState.AWAITING_NAME, emptyList(), session.orderId)
        return Reply.Text(ask("pide el nombre de la persona que recibe el pedido", "¿A nombre de quién es el pedido?"))
    }

    private suspend fun sendProductList(
        from: String,
        welcome: String,
        business: BusinessNumbers,
        session: SessionInfo,
        page: Int,
    ) {
        val catalog = productRepository.searchByStore(business.phone, business.idWhatsApp, null)
        if (catalog.isEmpty()) {
            sendText(from, welcome + "Todavía no tenemos productos en el catálogo.")
            return
        }
        sendListReply(from, welcome, productListOrStart(business, session, catalog, page, null) as Reply.List)
    }

    private suspend fun sendText(from: String, text: String) {
        val result = messageRepository.sendText(from, text.take(MAX_REPLY_LENGTH))
        track(from, result)
    }

    private suspend fun sendListReply(from: String, welcome: String, reply: Reply.List) {
        val body = (welcome + reply.body).take(MAX_REPLY_LENGTH)
        val pageButtons = reply.rows.count { it.id.startsWith(MORE_ID_PREFIX) }
        val result = if (reply.paged && pageButtons > 0) {
            // Keep "Ver más" in a section, product rows in another, so both render distinctly.
            val productRows = reply.rows.filterNot { it.id.startsWith(MORE_ID_PREFIX) }
            val moreRows = reply.rows.filter { it.id.startsWith(MORE_ID_PREFIX) }
            messageRepository.sendListSections(from, body, reply.buttonText, productRows, moreRows)
        } else {
            messageRepository.sendList(from, body, reply.buttonText, reply.rows)
        }
        track(from, result)
    }

    private suspend fun sendButtons(from: String, welcome: String, reply: Reply.Buttons) {
        val result = messageRepository.sendButtons(from, (welcome + reply.body).take(MAX_REPLY_LENGTH), reply.buttons)
        track(from, result)
    }

    private fun track(from: String, result: MessageResult) {
        when (result) {
            is MessageResult.Sent -> {
                replied.incrementAndGet()
                logger.info("Auto-reply sent to {} ({})", from, result.messageId)
            }

            else -> {
                logger.warn("Auto-reply to {} failed: {}", from, result)
                registerFailure("send: $result")
            }
        }
    }

    private suspend fun loadOrder(session: SessionInfo): OrderDraft? =
        session.orderId?.let { orderRepository.getOrder(it) }

    private suspend fun restartOrder(session: SessionInfo, message: String): Reply {
        saveState(session, SessionState.IDLE, emptyList(), null)
        return Reply.Text("$message ¿Qué deseas ordenar?")
    }

    private suspend fun saveState(
        session: SessionInfo,
        state: SessionState,
        options: List<Int>,
        orderId: Int? = session.orderId,
    ) {
        sessionRepository.saveState(session.id, session.storeId, state, options, orderId)
    }

    private suspend fun ask(instruction: String, fallback: String): String =
        when (val result = aiRepository.reply("$QUESTION_SYSTEM_PROMPT Pídele: $instruction.", "Genera la pregunta.")) {
            is AiResult.Reply -> result.text.takeIf { it.isNotBlank() } ?: fallback
            AiResult.NotConfigured -> fallback
            is AiResult.Failed -> {
                registerFailure("groq: ${result.detail}")
                fallback
            }
        }

    private fun productIdFrom(interactiveId: String?): Int? =
        interactiveId?.takeIf { it.startsWith(PRODUCT_ID_PREFIX) }
            ?.removePrefix(PRODUCT_ID_PREFIX)
            ?.toIntOrNull()

    private fun pageFor(text: String): Int =
        Regex("(\\d+)").find(text)?.value?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0

    private fun initialGreeting(store: StoreResponse): String =
        "${store.welcomeMessage}\n\n📍 Dirección: ${store.address}\n📞 Teléfono: ${store.phone}\n\n" +
            "En cualquier momento escribe *mi catálogo* para ver todos nuestros productos."

    private fun asksForCatalog(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return CATALOG_KEYWORDS.any { normalized.contains(it) }
    }

    private fun asksForStoreInfo(text: String): Boolean =
        STORE_INFO_KEYWORDS.any { text.lowercase().contains(it) }

    private fun isGreeting(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return normalized.length <= 25 && GREETING_WORDS.any { normalized.contains(it) }
    }

    private fun isAffirmative(text: String): Boolean =
        AFFIRMATIVE_WORDS.any { text == it || text.startsWith("$it ") || text.contains(" $it ") } ||
            text.startsWith("si")

    private fun isNegative(text: String): Boolean =
        NEGATIVE_WORDS.any { text == it || text.startsWith("$it ") || text.contains(" $it ") } ||
            text.startsWith("no")

    private fun storeInfo(store: StoreResponse): String =
        "📍 Dirección: ${store.address}\n📞 Teléfono: ${store.phone}"

    private fun describe(product: ProductResponse): String {
        val quantity = product.quantity
        return "$ ${product.price}${if (quantity > 0) " - disponibles: $quantity" else ""}"
    }

    private fun buildOrderSummary(order: OrderDraft): String = buildString {
        append("✅ ¡Pedido registrado!\n\n")
        append("Producto: ${order.productName}\n")
        append("Cantidad: ${order.quantity}\n")
        append("Total: $ ${order.total}\n")
        append("Nombre: ${order.customerName}\n")
        append("Dirección: ${order.deliveryAddress}\n")
        append("Pago: ${order.paymentType}\n\n")
        append("¡Gracias por tu compra!")
    }

    private fun parseNumbers(text: String): List<Int> =
        Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()

    private fun registerFailure(detail: String) {
        failed.incrementAndGet()
        lastError.set(detail)
    }

    private fun extractMessages(payload: WhatsAppWebhookPayload): List<IncomingMessage> =
        payload.entry.orEmpty()
            .flatMap { it.changes.orEmpty() }
            .filter { it.field == MESSAGES_FIELD }
            .flatMap { it.value?.messages.orEmpty() }
            .mapNotNull { message ->
                val from = message.from?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                when (message.type) {
                    TEXT_MESSAGE_TYPE -> {
                        val body = message.text?.body?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        IncomingMessage(message.id, from, body, null)
                    }

                    INTERACTIVE_MESSAGE_TYPE -> {
                        val id = message.interactive?.listReply?.id
                            ?: message.interactive?.buttonReply?.id
                            ?: return@mapNotNull null
                        IncomingMessage(message.id, from, null, id)
                    }

                    else -> null
                }
            }

    private fun extractBusiness(payload: WhatsAppWebhookPayload): BusinessNumbers {
        val metadata = payload.entry.orEmpty()
            .asSequence()
            .flatMap { it.changes.orEmpty().asSequence() }
            .mapNotNull { it.value?.metadata }
            .firstOrNull()
        return BusinessNumbers(metadata?.displayPhoneNumber, metadata?.phoneNumberId)
    }

    private fun markProcessed(messageId: String?): Boolean {
        if (messageId == null) return true
        if (processedMessageIds.size >= MAX_TRACKED_MESSAGE_IDS) processedMessageIds.clear()
        return processedMessageIds.add(messageId)
    }

    private fun constantTimeEquals(candidate: String, expected: String): Boolean =
        MessageDigest.isEqual(candidate.toByteArray(), expected.toByteArray())

    private sealed interface Reply {
        data class Text(val text: String) : Reply

        data class List(
            val body: String,
            val buttonText: String,
            val rows: kotlin.collections.List<MessageOption>,
            val paged: Boolean,
        ) : Reply

        data class Buttons(val body: String, val buttons: kotlin.collections.List<MessageOption>) : Reply
    }

    private data class IncomingMessage(
        val id: String?,
        val from: String,
        val text: String?,
        val interactiveId: String?,
    )

    private data class BusinessNumbers(
        val phone: String?,
        val idWhatsApp: String?,
    )
}

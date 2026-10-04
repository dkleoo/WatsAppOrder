package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.enum.SessionState
import com.example.watsapporderservices.data.mapper.ProductResponse
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.AiRepository
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
private const val MAX_REPLY_LENGTH = 4096
private const val MAX_TRACKED_MESSAGE_IDS = 1000
private const val QUESTION_SYSTEM_PROMPT =
    "Eres el asistente de pedidos de una tienda por WhatsApp. Devuelve SOLO una pregunta breve y clara, " +
        "sin listar productos y sin inventar datos."

private val STORE_INFO_KEYWORDS = listOf(
    "direccion", "dirección", "donde", "dónde", "ubicacion", "ubicación",
    "telefono", "teléfono", "contacto", "whatsapp",
)

private val GREETING_WORDS = listOf(
    "hola", "buenas", "buenos dias", "buenos días", "buenas tardes", "buenas noches",
    "hey", "hi", "hello", "saludos", "que tal", "qué tal",
)

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
        val messages = extractTextMessages(payload)
        receivedMessages.addAndGet(messages.size.toLong())
        logger.info("Webhook event received: {} text message(s)", messages.size)

        messages.forEach { incoming ->
            if (!markProcessed(incoming.id)) return@forEach

            val session = sessionRepository.resume(incoming.from, store?.id)
            val body = handleConversation(store, business, session, incoming.text)
            // The welcome + address + phone are sent only once, at the start of the session.
            val reply = if (session.isNew && store != null) "${initialGreeting(store)}\n\n$body" else body

            when (val sent = messageRepository.sendText(incoming.from, reply.take(MAX_REPLY_LENGTH))) {
                is MessageResult.Sent -> {
                    replied.incrementAndGet()
                    logger.info("Auto-reply sent to {} ({})", incoming.from, sent.messageId)
                }

                else -> {
                    logger.warn("Auto-reply to {} failed: {}", incoming.from, sent)
                    registerFailure("send: $sent")
                }
            }
        }
    }

    private suspend fun handleConversation(
        store: StoreResponse?,
        business: BusinessNumbers,
        session: SessionInfo,
        text: String,
    ): String = when (session.state) {
        SessionState.IDLE -> handleIdle(store, business, session, text)
        SessionState.SELECTING_PRODUCT -> handleProductSelection(session, text)
        SessionState.SELECTING_INGREDIENTS -> handleIngredients(session, text)
        SessionState.AWAITING_QUANTITY -> handleQuantity(session, text)
        SessionState.AWAITING_NAME -> handleName(session, text)
        SessionState.AWAITING_ADDRESS -> handleAddress(session, text)
        SessionState.AWAITING_PAYMENT -> handlePayment(session, text)
    }

    private suspend fun handleIdle(
        store: StoreResponse?,
        business: BusinessNumbers,
        session: SessionInfo,
        text: String,
    ): String {
        if (!session.isNew && store != null && asksForStoreInfo(text)) return storeInfo(store)

        if (isGreeting(text)) {
            return offerMenu(business, session)
        }

        val products = productRepository.searchByStore(business.phone, business.idWhatsApp, text)
        return when {
            products.isNotEmpty() -> offerProducts(session, products)
            else -> menuAfterNoMatch(business, session, text)
        }
    }

    private suspend fun offerMenu(business: BusinessNumbers, session: SessionInfo): String {
        val menu = productRepository.searchByStore(business.phone, business.idWhatsApp, null)
        if (menu.isEmpty()) return "¿Qué deseas ordenar?"
        saveState(session, SessionState.SELECTING_PRODUCT, menu.map { it.id })
        return "¿Qué deseas ordenar? Estos son nuestros productos:\n${buildOptions(menu)}\n" +
            "Responde con el número de la que deseas."
    }

    private suspend fun menuAfterNoMatch(
        business: BusinessNumbers,
        session: SessionInfo,
        text: String,
    ): String {
        val menu = productRepository.searchByStore(business.phone, business.idWhatsApp, null)
        if (menu.isEmpty()) return "No tengo productos disponibles por ahora."
        saveState(session, SessionState.SELECTING_PRODUCT, menu.map { it.id })
        return "No tengo \"$text\" en el menú. Estos son nuestros productos:\n${buildOptions(menu)}\n" +
            "Responde con el número de la que deseas."
    }

    private suspend fun offerProducts(session: SessionInfo, products: List<ProductResponse>): String {
        if (products.size == 1) return startProduct(session, products.single())
        saveState(session, SessionState.SELECTING_PRODUCT, products.map { it.id })
        return "Encontré varias opciones:\n${buildOptions(products)}\nResponde con el número de la que deseas."
    }

    private suspend fun handleProductSelection(
        session: SessionInfo,
        text: String,
    ): String {
        val index = text.trim().toIntOrNull()
        if (index == null || index !in 1..session.optionProductIds.size) {
            return "Responde con el número de una de las opciones."
        }
        val product = productRepository.getProduct(session.optionProductIds[index - 1])
            ?: return "No pude encontrar esa opción, intenta de nuevo."
        return startProduct(session, product)
    }

    private suspend fun startProduct(session: SessionInfo, product: ProductResponse): String {
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
            val list = inputs.mapIndexed { i, input -> "${i + 1}. ${input.name}" }.joinToString("\n")
            return "Elegiste ${product.name}. ¿Con qué ingredientes lo deseas?\n$list\n" +
                "Responde con los números (ej: 1,3)."
        }

        saveState(session, SessionState.AWAITING_QUANTITY, emptyList(), order.id)
        return "Elegiste ${product.name} ($ ${product.price}).\n" +
            ask("pide la cantidad que desea", "¿Cuántos deseas?")
    }

    private suspend fun handleIngredients(session: SessionInfo, text: String): String {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val chosen = parseNumbers(text).mapNotNull { n -> session.optionProductIds.getOrNull(n - 1) }
        if (chosen.isEmpty()) {
            return "Responde con los números de los ingredientes (ej: 1,3)."
        }
        orderRepository.save(order.copy(selectedInputIds = chosen.distinct()))
        saveState(session, SessionState.AWAITING_QUANTITY, emptyList(), order.id)
        return ask("pide la cantidad que desea", "¿Cuántos deseas?")
    }

    private suspend fun handleQuantity(session: SessionInfo, text: String): String {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val quantity = parseNumbers(text).firstOrNull()
        if (quantity == null) return "Indícame un número. ¿Cuántos deseas?"
        if (quantity <= 0) return "La cantidad debe ser mayor a 0. ¿Cuántos deseas?"
        orderRepository.save(order.copy(quantity = quantity))
        saveState(session, SessionState.AWAITING_NAME, emptyList(), order.id)
        return ask("pide el nombre de la persona que recibe el pedido", "¿A nombre de quién es el pedido?")
    }

    private suspend fun handleName(session: SessionInfo, text: String): String {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val name = text.trim().take(160)
        if (name.isBlank()) return "¿A nombre de quién es el pedido?"
        orderRepository.save(order.copy(customerName = name))
        saveState(session, SessionState.AWAITING_ADDRESS, emptyList(), order.id)
        return ask("pide la dirección de entrega", "¿Cuál es la dirección de entrega?")
    }

    private suspend fun handleAddress(session: SessionInfo, text: String): String {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val address = text.trim().take(300)
        if (address.isBlank()) return "¿Cuál es la dirección de entrega?"
        orderRepository.save(order.copy(deliveryAddress = address))
        saveState(session, SessionState.AWAITING_PAYMENT, emptyList(), order.id)
        return ask(
            "pide el tipo de pago (efectivo, transferencia, tarjeta, etc.)",
            "¿Cómo deseas pagar? (efectivo, transferencia, tarjeta...)",
        )
    }

    private suspend fun handlePayment(session: SessionInfo, text: String): String {
        val order = loadOrder(session) ?: return restartOrder(session, "Se reinició tu pedido.")
        val payment = text.trim().take(60)
        if (payment.isBlank()) return "¿Cómo deseas pagar? (efectivo, transferencia, tarjeta...)"

        val total = (order.unitPrice ?: 0.0) * (order.quantity ?: 0)
        val placed = orderRepository.save(
            order.copy(paymentType = payment, total = total, status = OrderStatus.PLACED),
        )
        saveState(session, SessionState.IDLE, emptyList(), null)
        return buildOrderSummary(placed)
    }

    private suspend fun loadOrder(session: SessionInfo): OrderDraft? =
        session.orderId?.let { orderRepository.getOrder(it) }

    private suspend fun restartOrder(session: SessionInfo, message: String): String {
        saveState(session, SessionState.IDLE, emptyList(), null)
        return "$message ¿Qué deseas ordenar?"
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

    private fun initialGreeting(store: StoreResponse): String =
        "${store.welcomeMessage}\n\n📍 Dirección: ${store.address}\n📞 Teléfono: ${store.phone}"

    private fun storeInfo(store: StoreResponse): String =
        "📍 Dirección: ${store.address}\n📞 Teléfono: ${store.phone}"

    private fun asksForStoreInfo(text: String): Boolean {
        val normalized = text.lowercase()
        return STORE_INFO_KEYWORDS.any { normalized.contains(it) }
    }

    private fun isGreeting(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return normalized.length <= 25 && GREETING_WORDS.any { normalized.contains(it) }
    }

    private fun buildOptions(products: List<ProductResponse>): String =
        products.mapIndexed { index, product -> "${index + 1}. ${describe(product)}" }.joinToString("\n")

    private fun describe(product: ProductResponse): String {
        val quantity = product.quantity
        return "${product.name} ($ ${product.price})${if (quantity > 0) " - disponibles: $quantity" else ""}"
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

    private fun extractTextMessages(payload: WhatsAppWebhookPayload): List<IncomingTextMessage> =
        payload.entry.orEmpty()
            .flatMap { it.changes.orEmpty() }
            .filter { it.field == MESSAGES_FIELD }
            .flatMap { it.value?.messages.orEmpty() }
            .filter { it.type == TEXT_MESSAGE_TYPE }
            .mapNotNull { message ->
                val from = message.from?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val body = message.text?.body?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                IncomingTextMessage(message.id, from, body)
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

    private data class IncomingTextMessage(
        val id: String?,
        val from: String,
        val text: String,
    )

    private data class BusinessNumbers(
        val phone: String?,
        val idWhatsApp: String?,
    )
}

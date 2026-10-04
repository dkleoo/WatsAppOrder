package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.enum.SessionState
import com.example.watsapporderservices.data.mapper.ProductResponse
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.AiRepository
import com.example.watsapporderservices.domain.repository.MessageRepository
import com.example.watsapporderservices.domain.repository.ProductRepository
import com.example.watsapporderservices.domain.repository.SessionRepository
import com.example.watsapporderservices.domain.repository.StoreRepository
import com.example.watsapporderservices.domain.repository.WebhookRepository
import com.example.watsapporderservices.domain.usecase.AiResult
import com.example.watsapporderservices.domain.usecase.MessageResult
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
private const val AI_NOT_CONFIGURED_REPLY = "El asistente automático no está disponible en este momento."
private const val AI_FAILED_REPLY = "Lo siento, no pude procesar tu mensaje. Intenta de nuevo en un momento."

class WebhookRepositoryImpl(
    private val config: WhatsAppConfig,
    private val aiRepository: AiRepository,
    private val messageRepository: MessageRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val sessionRepository: SessionRepository,
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
            val reply = buildReply(store, business, session, incoming.text)

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

    private suspend fun buildReply(
        store: StoreResponse?,
        business: BusinessNumbers,
        session: SessionInfo,
        text: String,
    ): String {
        val welcome = if (session.isNew && store != null) "${store.welcomeMessage}\n\n" else ""

        val selectedOption = text.trim().toIntOrNull()
        if (session.state == SessionState.SELECTING_PRODUCT &&
            selectedOption != null &&
            selectedOption in 1..session.optionProductIds.size
        ) {
            val product = productRepository.getProduct(session.optionProductIds[selectedOption - 1])
            sessionRepository.saveState(session.id, session.storeId, SessionState.IDLE, emptyList())
            return if (product != null) {
                "${welcome}¡Perfecto! Elegiste ${describe(product)}. ¿Confirmas tu pedido?"
            } else {
                "${welcome}No pude encontrar esa opción. ¿Podrías decirme de nuevo qué deseas?"
            }
        }

        val matching = productRepository.searchByStore(business.phone, business.idWhatsApp, text)
        return when {
            matching.size > 1 -> {
                sessionRepository.saveState(
                    session.id,
                    session.storeId,
                    SessionState.SELECTING_PRODUCT,
                    matching.map { it.id },
                )
                welcome + buildOptions(matching)
            }

            matching.size == 1 -> {
                sessionRepository.saveState(session.id, session.storeId, SessionState.IDLE, emptyList())
                "${welcome}Encontré ${describe(matching.single())}. ¿Deseas pedirlo? (responde *sí* o *no*)"
            }

            else -> {
                sessionRepository.saveState(session.id, session.storeId, SessionState.IDLE, emptyList())
                val menu = productRepository.searchByStore(business.phone, business.idWhatsApp, null)
                when (val result = aiRepository.reply(buildOrderAssistantPrompt(store, menu), text)) {
                    is AiResult.Reply -> welcome + result.text
                    AiResult.NotConfigured -> welcome + AI_NOT_CONFIGURED_REPLY
                    is AiResult.Failed -> {
                        logger.warn("Groq reply failed: {}", result.detail)
                        registerFailure("groq: ${result.detail}")
                        welcome + AI_FAILED_REPLY
                    }
                }
            }
        }
    }

    private fun buildOrdersText(store: StoreResponse?): String = buildString {
        append("Eres el asistente de pedidos de una tienda por WhatsApp. ")
        append("Tu tarea es tomar pedidos: pregunta qué desea ordenar y ayúdalo a elegir. ")
        if (store != null) append("Tienda -> dirección: ${store.address}; teléfono: ${store.phone}. ")
    }

    private fun buildOrderAssistantPrompt(store: StoreResponse?, products: List<ProductResponse>): String =
        buildString {
            append(buildOrdersText(store))
            if (products.isNotEmpty()) {
                append("Productos disponibles: ")
                products.forEach { append("${it.name} ($ ${it.price}); ") }
            }
            append("Usa SOLO esos productos y precios y no inventes otros. Responde breve, en el idioma del cliente.")
        }

    private fun buildOptions(products: List<ProductResponse>): String = buildString {
        append("Encontré varias opciones, responde con el número de la que deseas:\n")
        products.forEachIndexed { index, product -> append("${index + 1}. ${describe(product)}\n") }
    }

    private fun describe(product: ProductResponse): String {
        val quantity = product.quantity
        return "${product.name} ($ ${product.price})${if (quantity > 0) " - disponibles: $quantity" else ""}"
    }

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

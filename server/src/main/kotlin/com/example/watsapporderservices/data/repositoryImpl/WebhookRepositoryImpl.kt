package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.OrderAgentRepository
import com.example.watsapporderservices.domain.repository.StoreRepository
import com.example.watsapporderservices.domain.repository.WebhookRepository
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
private const val MAX_TRACKED_MESSAGE_IDS = 1000

/**
 * WhatsApp webhook: verifies Meta's handshake, deduplicates messages and hands the text to the
 * [OrderAgentRepository], which queries the database directly and sends the reply.
 */
class WebhookRepositoryImpl(
    private val config: WhatsAppConfig,
    private val storeRepository: StoreRepository,
    private val orderAgentRepository: OrderAgentRepository,
) : WebhookRepository {
    private val logger = LoggerFactory.getLogger(WebhookRepositoryImpl::class.java)
    private val processedMessageIds = ConcurrentHashMap.newKeySet<String>()

    private val receivedEvents = AtomicLong()
    private val receivedMessages = AtomicLong()
    private val handled = AtomicLong()
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
        replied = handled.get(),
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
            val text = incoming.text ?: incoming.interactiveId ?: return@forEach
            runCatching { orderAgentRepository.handleMessage(incoming.from, store, text) }
                .onSuccess { handled.incrementAndGet() }
                .onFailure {
                    logger.warn("Agent handling failed for {}: {}", incoming.from, it.message)
                    registerFailure("agent: ${it.message}")
                }
        }
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

    private fun registerFailure(detail: String) {
        failed.incrementAndGet()
        lastError.set(detail)
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

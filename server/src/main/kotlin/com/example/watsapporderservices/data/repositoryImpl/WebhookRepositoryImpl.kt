package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.AiRepository
import com.example.watsapporderservices.domain.repository.MessageRepository
import com.example.watsapporderservices.domain.repository.WebhookRepository
import com.example.watsapporderservices.domain.usecase.AiResult
import com.example.watsapporderservices.domain.usecase.MessageResult
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

        val messages = extractTextMessages(payload)
        receivedMessages.addAndGet(messages.size.toLong())
        logger.info("Webhook event received: {} text message(s)", messages.size)

        messages.forEach { incoming ->
            if (!markProcessed(incoming.id)) return@forEach

            val reply = when (val result = aiRepository.reply(incoming.text)) {
                is AiResult.Reply -> result.text
                AiResult.NotConfigured -> AI_NOT_CONFIGURED_REPLY
                is AiResult.Failed -> {
                    logger.warn("Groq reply failed for {}: {}", incoming.from, result.detail)
                    registerFailure("groq: ${result.detail}")
                    AI_FAILED_REPLY
                }
            }

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
}

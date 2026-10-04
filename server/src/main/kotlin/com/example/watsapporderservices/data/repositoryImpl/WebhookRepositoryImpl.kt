package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.AiRepository
import com.example.watsapporderservices.domain.repository.MessageRepository
import com.example.watsapporderservices.domain.repository.WebhookRepository
import com.example.watsapporderservices.domain.usecase.AiResult
import com.example.watsapporderservices.domain.usecase.MessageResult
import com.example.watsapporderservices.domain.usecase.WebhookResult
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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

    override suspend fun verify(mode: String?, token: String?, challenge: String?): WebhookResult {
        if (mode != SUBSCRIBE_MODE) return WebhookResult.Rejected
        if (challenge.isNullOrEmpty()) return WebhookResult.Rejected
        if (!config.isConfigured || token == null) return WebhookResult.Rejected
        if (!constantTimeEquals(token, config.verifyToken)) return WebhookResult.Rejected
        return WebhookResult.Verified(challenge)
    }

    override suspend fun handleEvent(payload: WhatsAppWebhookPayload) {
        extractTextMessages(payload).forEach { incoming ->
            if (!markProcessed(incoming.id)) return@forEach

            val reply = when (val result = aiRepository.reply(incoming.text)) {
                is AiResult.Reply -> result.text
                AiResult.NotConfigured -> AI_NOT_CONFIGURED_REPLY
                is AiResult.Failed -> {
                    logger.warn("Groq reply failed for {}: {}", incoming.from, result.detail)
                    AI_FAILED_REPLY
                }
            }

            when (val sent = messageRepository.sendText(incoming.from, reply.take(MAX_REPLY_LENGTH))) {
                is MessageResult.Sent -> logger.info("Auto-reply sent to {} ({})", incoming.from, sent.messageId)
                else -> logger.warn("Auto-reply to {} failed: {}", incoming.from, sent)
            }
        }
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

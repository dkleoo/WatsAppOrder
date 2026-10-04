package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.enum.MessageErrorCode
import com.example.watsapporderservices.data.mapper.SendMessageRequest
import com.example.watsapporderservices.data.mapper.WhatsAppErrorEnvelope
import com.example.watsapporderservices.data.mapper.WhatsAppSendResponse
import com.example.watsapporderservices.data.mapper.WhatsAppTextBody
import com.example.watsapporderservices.data.mapper.WhatsAppTextPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.MessageRepository
import com.example.watsapporderservices.domain.usecase.MessageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val MAX_EXTENSION_DIGITS = 4
private const val MIN_NUMBER_DIGITS = 6
private const val MAX_NUMBER_DIGITS = 15
private const val MAX_MESSAGE_LENGTH = 4096
private const val REQUEST_TIMEOUT_SECONDS = 15L

class MessageRepositoryImpl(
    private val config: WhatsAppConfig,
) : MessageRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build()

    override suspend fun send(request: SendMessageRequest): MessageResult {
        val extension = request.extension?.filter { it.isDigit() }.orEmpty()
        val number = request.number?.filter { it.isDigit() }.orEmpty()
        val message = request.message?.trim().orEmpty()

        if (extension.isEmpty() || extension.length > MAX_EXTENSION_DIGITS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_EXTENSION)
        }
        if (number.length !in MIN_NUMBER_DIGITS..MAX_NUMBER_DIGITS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_NUMBER)
        }
        if (message.isEmpty() || message.length > MAX_MESSAGE_LENGTH) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_MESSAGE)
        }
        if (!config.isSendConfigured) {
            return MessageResult.NotConfigured
        }

        val to = extension + number
        val payload = json.encodeToString(
            WhatsAppTextPayload.serializer(),
            WhatsAppTextPayload(to = to, text = WhatsAppTextBody(message)),
        )
        val httpRequest = HttpRequest.newBuilder(URI.create(messagesUrl()))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Authorization", "Bearer ${config.accessToken}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val attempt = withContext(Dispatchers.IO) {
            runCatching { httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()) }
        }
        val response = attempt.getOrElse { cause ->
            return MessageResult.ProviderError(cause.message ?: cause::class.simpleName)
        }

        if (response.statusCode() !in 200..299) {
            return MessageResult.ProviderError(parseErrorDetail(response.body()))
        }

        val messageId = runCatching {
            json.decodeFromString(WhatsAppSendResponse.serializer(), response.body())
                .messages?.firstOrNull()?.id
        }.getOrNull() ?: return MessageResult.ProviderError("Malformed response from WhatsApp Cloud API")

        return MessageResult.Sent(messageId, to)
    }

    private fun parseErrorDetail(body: String): String? = runCatching {
        val error = json.decodeFromString(WhatsAppErrorEnvelope.serializer(), body).error
        listOfNotNull(error?.code?.toString(), error?.message).joinToString(" ").ifBlank { null }
    }.getOrNull()

    private fun messagesUrl(): String =
        "https://graph.facebook.com/${config.graphApiVersion}/${config.phoneNumberId}/messages"
}

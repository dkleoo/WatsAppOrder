package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.enum.MessageErrorCode
import com.example.watsapporderservices.data.mapper.SendMessageRequest
import com.example.watsapporderservices.data.mapper.WhatsAppErrorEnvelope
import com.example.watsapporderservices.data.mapper.WhatsAppButtonsAction
import com.example.watsapporderservices.data.mapper.WhatsAppButtonsInteractive
import com.example.watsapporderservices.data.mapper.WhatsAppButtonsPayload
import com.example.watsapporderservices.data.mapper.WhatsAppListAction
import com.example.watsapporderservices.data.mapper.WhatsAppListInteractive
import com.example.watsapporderservices.data.mapper.WhatsAppListPayload
import com.example.watsapporderservices.data.mapper.WhatsAppListRow
import com.example.watsapporderservices.data.mapper.WhatsAppListSection
import com.example.watsapporderservices.data.mapper.WhatsAppReplyButton
import com.example.watsapporderservices.data.mapper.WhatsAppReplyRef
import com.example.watsapporderservices.data.mapper.WhatsAppSendResponse
import com.example.watsapporderservices.data.mapper.WhatsAppInteractiveText
import com.example.watsapporderservices.data.mapper.WhatsAppTextBody
import com.example.watsapporderservices.data.mapper.WhatsAppTextPayload
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.domain.repository.MessageOption
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
private const val MAX_LIST_ROWS = 10
private const val MAX_BUTTONS = 3
private const val MAX_ROW_TITLE_LENGTH = 24
private const val MAX_ROW_DESCRIPTION_LENGTH = 72
private const val MAX_LIST_BODY_LENGTH = 1024
private const val MAX_BUTTON_TITLE_LENGTH = 20

class MessageRepositoryImpl(
    private val config: WhatsAppConfig,
) : MessageRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
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

        return sendTo(extension + number, message)
    }

    override suspend fun sendText(to: String, message: String): MessageResult {
        val toDigits = to.filter { it.isDigit() }
        val text = message.trim()

        if (toDigits.length !in MIN_NUMBER_DIGITS..MAX_NUMBER_DIGITS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_NUMBER)
        }
        if (text.isEmpty() || text.length > MAX_MESSAGE_LENGTH) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_MESSAGE)
        }
        if (!config.isSendConfigured) {
            return MessageResult.NotConfigured
        }

        return sendTo(toDigits, text)
    }

    private suspend fun sendTo(to: String, message: String): MessageResult {
        val payload = json.encodeToString(
            WhatsAppTextPayload.serializer(),
            WhatsAppTextPayload(to = to, text = WhatsAppTextBody(message)),
        )
        return post(to, payload)
    }

    override suspend fun sendList(
        to: String,
        body: String,
        buttonText: String,
        rows: List<MessageOption>,
    ): MessageResult {
        val toDigits = to.filter { it.isDigit() }
        if (toDigits.length !in MIN_NUMBER_DIGITS..MAX_NUMBER_DIGITS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_NUMBER)
        }
        if (rows.isEmpty() || rows.size > MAX_LIST_ROWS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_MESSAGE)
        }
        if (!config.isSendConfigured) return MessageResult.NotConfigured

        val payload = json.encodeToString(
            WhatsAppListPayload.serializer(),
            WhatsAppListPayload(
                to = toDigits,
                interactive = WhatsAppListInteractive(
                    body = WhatsAppInteractiveText(text = body.take(MAX_LIST_BODY_LENGTH)),
                    action = WhatsAppListAction(
                        button = buttonText.take(MAX_BUTTON_TITLE_LENGTH),
                        sections = listOf(
                            WhatsAppListSection(
                                title = "Productos",
                                rows = rows.map {
                                    WhatsAppListRow(
                                        id = it.id,
                                        title = it.title.take(MAX_ROW_TITLE_LENGTH),
                                        description = it.description?.take(MAX_ROW_DESCRIPTION_LENGTH),
                                    )
                                },
                            ),
                        ),
                    ),
                ),
            ),
        )
        return post(toDigits, payload)
    }

    override suspend fun sendListSections(
        to: String,
        body: String,
        buttonText: String,
        productRows: List<MessageOption>,
        moreRows: List<MessageOption>,
    ): MessageResult {
        val toDigits = to.filter { it.isDigit() }
        if (toDigits.length !in MIN_NUMBER_DIGITS..MAX_NUMBER_DIGITS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_NUMBER)
        }
        val total = productRows.size + moreRows.size
        if (total == 0 || total > MAX_LIST_ROWS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_MESSAGE)
        }
        if (!config.isSendConfigured) return MessageResult.NotConfigured

        val sections = buildList {
            if (productRows.isNotEmpty()) {
                add(WhatsAppListSection(title = "Productos", rows = productRows.map { it.toRow() }))
            }
            if (moreRows.isNotEmpty()) {
                add(WhatsAppListSection(title = "Más", rows = moreRows.map { it.toRow() }))
            }
        }
        val payload = json.encodeToString(
            WhatsAppListPayload.serializer(),
            WhatsAppListPayload(
                to = toDigits,
                interactive = WhatsAppListInteractive(
                    body = WhatsAppInteractiveText(text = body.take(MAX_LIST_BODY_LENGTH)),
                    action = WhatsAppListAction(
                        button = buttonText.take(MAX_BUTTON_TITLE_LENGTH),
                        sections = sections,
                    ),
                ),
            ),
        )
        return post(toDigits, payload)
    }

    override suspend fun sendButtons(
        to: String,
        body: String,
        buttons: List<MessageOption>,
    ): MessageResult {
        val toDigits = to.filter { it.isDigit() }
        if (toDigits.length !in MIN_NUMBER_DIGITS..MAX_NUMBER_DIGITS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_NUMBER)
        }
        if (buttons.isEmpty() || buttons.size > MAX_BUTTONS) {
            return MessageResult.Invalid(MessageErrorCode.INVALID_MESSAGE)
        }
        if (!config.isSendConfigured) return MessageResult.NotConfigured

        val payload = json.encodeToString(
            WhatsAppButtonsPayload.serializer(),
            WhatsAppButtonsPayload(
                to = toDigits,
                interactive = WhatsAppButtonsInteractive(
                    body = WhatsAppInteractiveText(text = body),
                    action = WhatsAppButtonsAction(
                        buttons = buttons.map {
                            WhatsAppReplyButton(
                                reply = WhatsAppReplyRef(
                                    id = it.id,
                                    title = it.title.take(MAX_BUTTON_TITLE_LENGTH),
                                ),
                            )
                        },
                    ),
                ),
            ),
        )
        return post(toDigits, payload)
    }

    private suspend fun post(to: String, payload: String): MessageResult {
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

    private fun MessageOption.toRow(): WhatsAppListRow = WhatsAppListRow(
        id = id,
        title = title.take(MAX_ROW_TITLE_LENGTH),
        description = description?.take(MAX_ROW_DESCRIPTION_LENGTH),
    )
}

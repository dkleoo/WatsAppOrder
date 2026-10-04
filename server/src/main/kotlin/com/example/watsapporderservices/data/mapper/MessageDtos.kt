package com.example.watsapporderservices.data.mapper

import com.example.watsapporderservices.data.enum.MessageErrorCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendMessageRequest(
    val extension: String? = null,
    val number: String? = null,
    val message: String? = null,
)

@Serializable
data class SendMessageResponse(
    val messageId: String,
    val to: String,
)

@Serializable
data class ProviderErrorResponse(
    val error: String,
    val detail: String? = null,
)

fun MessageErrorCode.toResponse(): ErrorResponse = ErrorResponse(code)

/** Payload sent to the WhatsApp Cloud API (`POST /{phoneNumberId}/messages`). */
@Serializable
internal data class WhatsAppTextPayload(
    @SerialName("messaging_product") val messagingProduct: String = "whatsapp",
    val to: String,
    val type: String = "text",
    val text: WhatsAppTextBody,
)

@Serializable
internal data class WhatsAppTextBody(
    val body: String,
)

@Serializable
internal data class WhatsAppSendResponse(
    val messages: List<WhatsAppSentMessage>? = null,
)

@Serializable
internal data class WhatsAppSentMessage(
    val id: String,
)

@Serializable
internal data class WhatsAppErrorEnvelope(
    val error: WhatsAppError? = null,
)

@Serializable
internal data class WhatsAppError(
    val message: String? = null,
    val type: String? = null,
    val code: Long? = null,
)

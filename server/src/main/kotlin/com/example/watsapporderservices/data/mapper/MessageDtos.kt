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

/** Interactive list message (max 10 rows per list). */
@Serializable
internal data class WhatsAppListPayload(
    @SerialName("messaging_product") val messagingProduct: String = "whatsapp",
    val to: String,
    val type: String = "interactive",
    val interactive: WhatsAppListInteractive,
)

@Serializable
internal data class WhatsAppListInteractive(
    val type: String = "list",
    val header: WhatsAppInteractiveText? = null,
    val body: WhatsAppTextBody,
    val action: WhatsAppListAction,
)

@Serializable
internal data class WhatsAppListAction(
    val button: String,
    val sections: List<WhatsAppListSection>,
)

@Serializable
internal data class WhatsAppListSection(
    val title: String,
    val rows: List<WhatsAppListRow>,
)

@Serializable
internal data class WhatsAppListRow(
    val id: String,
    val title: String,
    val description: String? = null,
)

/** Interactive button message (max 3 buttons). */
@Serializable
internal data class WhatsAppButtonsPayload(
    @SerialName("messaging_product") val messagingProduct: String = "whatsapp",
    val to: String,
    val type: String = "interactive",
    val interactive: WhatsAppButtonsInteractive,
)

@Serializable
internal data class WhatsAppButtonsInteractive(
    val type: String = "button",
    val body: WhatsAppTextBody,
    val action: WhatsAppButtonsAction,
)

@Serializable
internal data class WhatsAppButtonsAction(
    val buttons: List<WhatsAppReplyButton>,
)

@Serializable
internal data class WhatsAppReplyButton(
    val type: String = "reply",
    val reply: WhatsAppReplyRef,
)

@Serializable
internal data class WhatsAppReplyRef(
    val id: String,
    val title: String,
)

@Serializable
internal data class WhatsAppInteractiveText(
    val type: String = "text",
    val text: String,
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

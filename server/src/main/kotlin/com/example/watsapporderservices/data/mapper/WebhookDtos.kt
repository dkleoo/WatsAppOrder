package com.example.watsapporderservices.data.mapper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppWebhookPayload(
    @SerialName("object") val obj: String? = null,
    val entry: List<WhatsAppEntry>? = null,
)

@Serializable
data class WhatsAppEntry(
    val id: String? = null,
    val changes: List<WhatsAppChange>? = null,
)

@Serializable
data class WhatsAppChange(
    val field: String? = null,
    val value: WhatsAppChangeValue? = null,
)

@Serializable
data class WhatsAppChangeValue(
    @SerialName("messaging_product") val messagingProduct: String? = null,
    val metadata: WhatsAppMetadata? = null,
    val contacts: List<WhatsAppContact>? = null,
    val messages: List<WhatsAppIncomingMessage>? = null,
)

@Serializable
data class WhatsAppMetadata(
    @SerialName("display_phone_number") val displayPhoneNumber: String? = null,
    @SerialName("phone_number_id") val phoneNumberId: String? = null,
)

@Serializable
data class WhatsAppContact(
    val profile: WhatsAppProfile? = null,
    @SerialName("wa_id") val waId: String? = null,
)

@Serializable
data class WhatsAppProfile(
    val name: String? = null,
)

@Serializable
data class WhatsAppIncomingMessage(
    val from: String? = null,
    val id: String? = null,
    val timestamp: String? = null,
    val type: String? = null,
    val text: WhatsAppIncomingText? = null,
)

@Serializable
data class WhatsAppIncomingText(
    val body: String? = null,
)

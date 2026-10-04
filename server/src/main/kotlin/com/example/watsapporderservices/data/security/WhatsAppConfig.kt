package com.example.watsapporderservices.data.security

import io.ktor.server.config.ApplicationConfig

data class WhatsAppConfig(
    val verifyToken: String,
) {
    val isConfigured: Boolean get() = verifyToken.isNotBlank()

    companion object {
        fun from(config: ApplicationConfig, env: Map<String, String>): WhatsAppConfig = WhatsAppConfig(
            verifyToken = config.propertyOrNull("whatsapp.verifyToken")?.getString()?.takeIf { it.isNotBlank() }
                ?: env["WHATSAPP_VERIFY_TOKEN"]?.takeIf { it.isNotBlank() }
                ?: "",
        )
    }
}

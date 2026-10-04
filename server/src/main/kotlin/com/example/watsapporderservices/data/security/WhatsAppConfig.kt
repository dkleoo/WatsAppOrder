package com.example.watsapporderservices.data.security

import io.ktor.server.config.ApplicationConfig

private const val DEFAULT_GRAPH_API_VERSION = "v25.0"

data class WhatsAppConfig(
    val verifyToken: String,
    val accessToken: String,
    val businessAccountId: String,
    val phoneNumberId: String,
    val graphApiVersion: String,
) {
    val isConfigured: Boolean get() = verifyToken.isNotBlank()

    val isSendConfigured: Boolean get() = accessToken.isNotBlank() && phoneNumberId.isNotBlank()

    companion object {
        fun from(config: ApplicationConfig, env: Map<String, String>): WhatsAppConfig = WhatsAppConfig(
            verifyToken = config.valueOrEnv("whatsapp.verifyToken", env, "WHATSAPP_VERIFY_TOKEN") ?: "",
            accessToken = config.valueOrEnv(
                "whatsapp.accessToken",
                env,
                "WHATSAPP_ACCESS_TOKEN",
                "WHATAPP_PERMANT",
            ) ?: "",
            businessAccountId = config.valueOrEnv(
                "whatsapp.businessAccountId",
                env,
                "WHATSAPP_BUSINESS_ACCOUNT_ID",
            ) ?: "",
            phoneNumberId = config.valueOrEnv("whatsapp.phoneNumberId", env, "WHATSAPP_PHONE_NUMBER_ID") ?: "",
            graphApiVersion = config.valueOrEnv(
                "whatsapp.graphApiVersion",
                env,
                "WHATSAPP_GRAPH_API_VERSION",
            ) ?: DEFAULT_GRAPH_API_VERSION,
        )

        private fun ApplicationConfig.valueOrEnv(
            path: String,
            env: Map<String, String>,
            vararg envKeys: String,
        ): String? {
            propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }?.let { return it }
            return envKeys.firstNotNullOfOrNull { env[it]?.takeIf { value -> value.isNotBlank() } }
        }
    }
}

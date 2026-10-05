package com.example.watsapporderservices.data.security

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class FirebaseServiceAccount(
    val projectId: String,
    val clientEmail: String,
    val privateKey: String,
)

data class PushConfig(
    val projectId: String,
    val serviceAccount: FirebaseServiceAccount?,
) {
    val isConfigured: Boolean get() = serviceAccount != null && projectId.isNotBlank()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(config: ApplicationConfig, env: Map<String, String>): PushConfig {
            val projectId = config.propertyOrNull("firebase.projectId")?.getString()?.takeIf { it.isNotBlank() }
                ?: env["FIREBASE_PROJECT_ID"]?.takeIf { it.isNotBlank() }
                ?: ""

            val raw = env["FIREBASE_SERVICE_ACCOUNT_JSON"]?.takeIf { it.isNotBlank() }
                ?: env["FIREBASE_SERVICE_ACCOUNT_BASE64"]?.takeIf { it.isNotBlank() }?.let { decodeBase64(it) }

            val account = raw?.let { parseServiceAccount(it) }
            return PushConfig(projectId.ifBlank { account?.projectId.orEmpty() }, account)
        }

        private fun parseServiceAccount(raw: String): FirebaseServiceAccount? = runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val privateKey = obj["private_key"]?.jsonPrimitive?.content.orEmpty()
            FirebaseServiceAccount(
                projectId = obj["project_id"]?.jsonPrimitive?.content.orEmpty(),
                clientEmail = obj["client_email"]?.jsonPrimitive?.content.orEmpty(),
                // The key comes with literal "\n" sequences; restore real newlines for the PEM parser.
                privateKey = privateKey.replace("\\n", "\n"),
            )
        }.getOrNull()

        private fun decodeBase64(value: String): String? = runCatching {
            String(java.util.Base64.getDecoder().decode(value), Charsets.UTF_8)
        }.getOrNull()
    }
}

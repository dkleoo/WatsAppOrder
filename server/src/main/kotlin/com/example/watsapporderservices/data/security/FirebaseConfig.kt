package com.example.watsapporderservices.data.security

import io.ktor.server.config.ApplicationConfig

data class FirebaseConfig(
    val projectId: String,
) {
    val isConfigured: Boolean get() = projectId.isNotBlank()

    companion object {
        fun from(config: ApplicationConfig, env: Map<String, String>): FirebaseConfig = FirebaseConfig(
            projectId = config.propertyOrNull("firebase.projectId")?.getString()?.takeIf { it.isNotBlank() }
                ?: env["FIREBASE_PROJECT_ID"]?.takeIf { it.isNotBlank() }
                ?: "",
        )
    }
}

package com.example.watsapporderservices.data.database

import io.ktor.server.config.ApplicationConfig
import java.net.URI

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
) {
    companion object {
        private const val DEFAULT_MAX_POOL_SIZE = 10
        private const val DEFAULT_PORT = 5432
        private const val DEFAULT_DATABASE = "orderwhatsapp"

        fun from(config: ApplicationConfig): DatabaseConfig {
            val rawUrl = config.propertyOrNull("database.url")?.getString()?.takeIf { it.isNotBlank() }
                ?: error("DATABASE_URL is not set")
            val configuredUser = config.propertyOrNull("database.user")?.getString().orEmpty()
            val configuredPassword = config.propertyOrNull("database.password")?.getString().orEmpty()
            val maxPoolSize = config.propertyOrNull("database.maxPoolSize")?.getString()?.toIntOrNull()
                ?: DEFAULT_MAX_POOL_SIZE
            if (rawUrl.startsWith("jdbc:")) {
                return DatabaseConfig(rawUrl, configuredUser, configuredPassword, maxPoolSize)
            }
            val parsed = parseConnectionString(rawUrl)
            return DatabaseConfig(
                url = parsed.url,
                user = configuredUser.ifBlank { parsed.user },
                password = configuredPassword.ifBlank { parsed.password },
                maxPoolSize = maxPoolSize,
            )
        }

        private fun parseConnectionString(connectionString: String): DatabaseConfig {
            val uri = URI(connectionString.replaceFirst("postgres://", "postgresql://"))
            val credentials = uri.userInfo?.split(":", limit = 2).orEmpty()
            val host = uri.host ?: "localhost"
            val port = if (uri.port > 0) uri.port else DEFAULT_PORT
            val database = uri.path.removePrefix("/").substringBefore("?").ifBlank { DEFAULT_DATABASE }
            val isLocal = host == "localhost" || host == "127.0.0.1" || host == "::1"
            val sslMode = if (!isLocal && host.contains('.')) "?sslmode=require" else ""
            return DatabaseConfig(
                url = "jdbc:postgresql://$host:$port/$database$sslMode",
                user = credentials.getOrNull(0).orEmpty(),
                password = credentials.getOrNull(1).orEmpty(),
                maxPoolSize = DEFAULT_MAX_POOL_SIZE,
            )
        }
    }
}

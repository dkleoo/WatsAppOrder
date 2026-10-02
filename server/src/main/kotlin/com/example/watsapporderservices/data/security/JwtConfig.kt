package com.example.watsapporderservices.data.security

import io.ktor.server.config.ApplicationConfig

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expirationMinutes: Long,
) {
    val expirationMillis: Long get() = expirationMinutes * 60_000

    companion object {
        private const val DEFAULT_SECRET = "watsapp-order-development-secret-key-change-me"
        private const val DEFAULT_ISSUER = "watsapp-order"
        private const val DEFAULT_AUDIENCE = "watsapp-order-clients"
        private const val DEFAULT_REALM = "watsapp-order"
        private const val DEFAULT_EXPIRATION_MINUTES = 60L

        fun from(config: ApplicationConfig, env: Map<String, String>): JwtConfig = JwtConfig(
            secret = config.valueOrEnv("jwt.secret", env, "JWT_SECRET") ?: DEFAULT_SECRET,
            issuer = config.valueOrEnv("jwt.issuer", env, "JWT_ISSUER") ?: DEFAULT_ISSUER,
            audience = config.valueOrEnv("jwt.audience", env, "JWT_AUDIENCE") ?: DEFAULT_AUDIENCE,
            realm = config.valueOrEnv("jwt.realm", env, "JWT_REALM") ?: DEFAULT_REALM,
            expirationMinutes = config.valueOrEnv("jwt.expirationMinutes", env, "JWT_EXPIRATION_MINUTES")
                ?.toLongOrNull() ?: DEFAULT_EXPIRATION_MINUTES,
        )

        private fun ApplicationConfig.valueOrEnv(
            path: String,
            env: Map<String, String>,
            envKey: String,
        ): String? = propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }
            ?: env[envKey]?.takeIf { it.isNotBlank() }
    }
}

package com.example.watsapporderservices.data.security

import io.ktor.server.config.ApplicationConfig

/**
 * OpenCode Zen gateway. OpenAI-compatible chat completions endpoint.
 * Docs: https://opencode.ai/docs/zen/
 */
data class OpenCodeConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val topP: Double,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val DEFAULT_BASE_URL = "https://opencode.ai/inference/v1/chat/completions"
        private const val DEFAULT_MODEL = "deepseek-v4.1-flash"
        private const val DEFAULT_TEMPERATURE = 0.3
        private const val DEFAULT_TOP_P = 1.0

        fun from(config: ApplicationConfig, env: Map<String, String>): OpenCodeConfig = OpenCodeConfig(
            apiKey = config.valueOrEnv("opencode.apiKey", env, "OPENCODE_API_KEY") ?: "",
            baseUrl = config.valueOrEnv("opencode.baseUrl", env, "OPENCODE_BASE_URL") ?: DEFAULT_BASE_URL,
            model = config.valueOrEnv("opencode.model", env, "OPENCODE_MODEL") ?: DEFAULT_MODEL,
            temperature = config.valueOrEnv("opencode.temperature", env, "OPENCODE_TEMPERATURE")?.toDoubleOrNull()
                ?: DEFAULT_TEMPERATURE,
            topP = config.valueOrEnv("opencode.topP", env, "OPENCODE_TOP_P")?.toDoubleOrNull() ?: DEFAULT_TOP_P,
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

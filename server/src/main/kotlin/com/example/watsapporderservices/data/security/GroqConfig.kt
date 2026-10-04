package com.example.watsapporderservices.data.security

import io.ktor.server.config.ApplicationConfig

data class GroqConfig(
    val apiKey: String,
    val model: String,
    val temperature: Double,
    val maxCompletionTokens: Int,
    val topP: Double,
    val reasoningEffort: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val DEFAULT_MODEL = "openai/gpt-oss-120b"
        private const val DEFAULT_TEMPERATURE = 1.0
        private const val DEFAULT_MAX_COMPLETION_TOKENS = 2048
        private const val DEFAULT_TOP_P = 1.0
        private const val DEFAULT_REASONING_EFFORT = "medium"

        fun from(config: ApplicationConfig, env: Map<String, String>): GroqConfig = GroqConfig(
            apiKey = config.valueOrEnv("groq.apiKey", env, "GROQ_API_KEY", "TOKEN_GROK") ?: "",
            model = config.valueOrEnv("groq.model", env, "GROQ_MODEL") ?: DEFAULT_MODEL,
            temperature = config.valueOrEnv("groq.temperature", env, "GROQ_TEMPERATURE")?.toDoubleOrNull()
                ?: DEFAULT_TEMPERATURE,
            maxCompletionTokens = config.valueOrEnv(
                "groq.maxCompletionTokens",
                env,
                "GROQ_MAX_COMPLETION_TOKENS",
            )?.toIntOrNull() ?: DEFAULT_MAX_COMPLETION_TOKENS,
            topP = config.valueOrEnv("groq.topP", env, "GROQ_TOP_P")?.toDoubleOrNull() ?: DEFAULT_TOP_P,
            reasoningEffort = config.valueOrEnv(
                "groq.reasoningEffort",
                env,
                "GROQ_REASONING_EFFORT",
            ) ?: DEFAULT_REASONING_EFFORT,
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

package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.mapper.GroqChatRequest
import com.example.watsapporderservices.data.mapper.GroqChatResponse
import com.example.watsapporderservices.data.mapper.GroqMessage
import com.example.watsapporderservices.data.security.GroqConfig
import com.example.watsapporderservices.domain.repository.AiRepository
import com.example.watsapporderservices.domain.usecase.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val CHAT_COMPLETIONS_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val REQUEST_TIMEOUT_SECONDS = 60L
private const val ERROR_BODY_SNIPPET = 300

private const val DEFAULT_SYSTEM_PROMPT =
    "Eres el asistente virtual de la tienda. Respondes por WhatsApp de forma breve, clara y amable, " +
        "en el mismo idioma del cliente."

class AiRepositoryImpl(
    private val config: GroqConfig,
) : AiRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build()

    override suspend fun reply(systemPrompt: String, message: String): AiResult {
        if (!config.isConfigured) return AiResult.NotConfigured

        val body = json.encodeToString(
            GroqChatRequest.serializer(),
            GroqChatRequest(
                model = config.model,
                messages = listOf(
                    GroqMessage(
                        role = "system",
                        content = systemPrompt.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT,
                    ),
                    GroqMessage(role = "user", content = message),
                ),
                temperature = config.temperature,
                maxCompletionTokens = config.maxCompletionTokens,
                topP = config.topP,
                reasoningEffort = config.reasoningEffort,
            ),
        )
        val httpRequest = HttpRequest.newBuilder(URI.create(CHAT_COMPLETIONS_URL))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val attempt = withContext(Dispatchers.IO) {
            runCatching { httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()) }
        }
        val response = attempt.getOrElse { cause ->
            return AiResult.Failed(cause.message ?: cause::class.simpleName)
        }

        if (response.statusCode() !in 200..299) {
            return AiResult.Failed("HTTP ${response.statusCode()}: ${response.body().take(ERROR_BODY_SNIPPET)}")
        }

        val content = runCatching {
            json.decodeFromString(GroqChatResponse.serializer(), response.body())
                .choices?.firstOrNull()?.message?.content
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return AiResult.Failed("Empty completion from Groq")

        return AiResult.Reply(content)
    }
}

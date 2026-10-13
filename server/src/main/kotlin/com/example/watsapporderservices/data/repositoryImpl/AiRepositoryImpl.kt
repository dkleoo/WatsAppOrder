package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.mapper.GroqChatRequest
import com.example.watsapporderservices.data.mapper.GroqChatResponse
import com.example.watsapporderservices.data.mapper.GroqFunctionCall
import com.example.watsapporderservices.data.mapper.GroqMessage
import com.example.watsapporderservices.data.mapper.GroqTool
import com.example.watsapporderservices.data.mapper.GroqToolCall
import com.example.watsapporderservices.data.mapper.GroqToolFunction
import com.example.watsapporderservices.data.security.GroqConfig
import com.example.watsapporderservices.domain.repository.AiRepository
import com.example.watsapporderservices.domain.usecase.AiChatResult
import com.example.watsapporderservices.domain.usecase.AiMessage
import com.example.watsapporderservices.domain.usecase.AiTool
import com.example.watsapporderservices.domain.usecase.AiToolCall
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
private const val REASONING_MODEL_MARKER = "gpt-oss"
private const val TOOL_CHOICE_AUTO = "auto"

class AiRepositoryImpl(
    private val config: GroqConfig,
) : AiRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build()

    override suspend fun chat(messages: List<AiMessage>, tools: List<AiTool>): AiChatResult {
        if (!config.isConfigured) return AiChatResult.NotConfigured

        return when (val result = send(messages.map { it.toGroq() }, tools)) {
            is SendResult.Ok -> {
                if (result.toolCalls.isNotEmpty()) {
                    AiChatResult.ToolCalls(result.toolCalls)
                } else {
                    AiChatResult.Message(result.content?.takeIf { it.isNotBlank() })
                }
            }

            is SendResult.Error -> AiChatResult.Failed(result.detail)
        }
    }

    private suspend fun send(messages: List<GroqMessage>, tools: List<AiTool>?): SendResult {
        val body = json.encodeToString(
            GroqChatRequest.serializer(),
            GroqChatRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                maxCompletionTokens = config.maxCompletionTokens,
                topP = config.topP,
                reasoningEffort = config.reasoningEffort.takeIf { config.model.contains(REASONING_MODEL_MARKER) },
                tools = tools?.takeIf { it.isNotEmpty() }?.map { it.toGroq() },
                toolChoice = TOOL_CHOICE_AUTO.takeIf { !tools.isNullOrEmpty() },
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
            return SendResult.Error(cause.message ?: cause::class.simpleName ?: "request failed")
        }
        if (response.statusCode() !in 200..299) {
            return SendResult.Error("HTTP ${response.statusCode()}: ${response.body().take(ERROR_BODY_SNIPPET)}")
        }

        val message = runCatching {
            json.decodeFromString(GroqChatResponse.serializer(), response.body()).choices?.firstOrNull()?.message
        }.getOrNull() ?: return SendResult.Error("Malformed response from Groq")

        val calls = message.toolCalls.orEmpty().map { AiToolCall(it.id, it.function.name, it.function.arguments) }
        return SendResult.Ok(message.content, calls)
    }
}

private fun AiMessage.toGroq(): GroqMessage = GroqMessage(
    role = role,
    content = content,
    toolCalls = toolCalls.takeIf { it.isNotEmpty() }
        ?.map { GroqToolCall(id = it.id, function = GroqFunctionCall(it.name, it.arguments)) },
    toolCallId = toolCallId,
)

private fun AiTool.toGroq(): GroqTool = GroqTool(
    function = GroqToolFunction(name = name, description = description, parameters = parameters),
)

private sealed interface SendResult {
    data class Ok(val content: String?, val toolCalls: List<AiToolCall>) : SendResult

    data class Error(val detail: String) : SendResult
}

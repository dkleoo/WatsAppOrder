package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.mapper.ChatCompletionRequest
import com.example.watsapporderservices.data.mapper.ChatCompletionResponse
import com.example.watsapporderservices.data.mapper.ChatFunctionCallDto
import com.example.watsapporderservices.data.mapper.ChatMessageDto
import com.example.watsapporderservices.data.mapper.ChatToolCallDto
import com.example.watsapporderservices.data.mapper.ChatToolDto
import com.example.watsapporderservices.data.mapper.ChatToolFunctionDto
import com.example.watsapporderservices.data.security.OpenCodeConfig
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

private const val REQUEST_TIMEOUT_SECONDS = 60L
private const val ERROR_BODY_SNIPPET = 300
private const val TOOL_CHOICE_AUTO = "auto"
private const val USER_AGENT = "watsapporder/1.0"
private const val SESSION_HEADER = "x-opencode-session"

/**
 * OpenCode Zen client (OpenAI-compatible chat completions). Sends the conversation plus the tool
 * definitions and returns either the assistant text or the tool calls to execute.
 */
class AiRepositoryImpl(
    private val config: OpenCodeConfig,
) : AiRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build()

    override suspend fun chat(sessionId: String, messages: List<AiMessage>, tools: List<AiTool>): AiChatResult {
        if (!config.isConfigured) return AiChatResult.NotConfigured

        return when (val result = send(sessionId, messages.map { it.toChat() }, tools)) {
            is SendResult.Ok -> {
                if (result.toolCalls.isNotEmpty()) {
                    AiChatResult.ToolCalls(result.toolCalls)
                } else {
                    val content = result.content?.takeIf { it.isNotBlank() }
                    if (content == null) {
                        AiChatResult.Failed("Empty completion (finish_reason=${result.finishReason})")
                    } else {
                        AiChatResult.Message(content)
                    }
                }
            }

            is SendResult.Error -> AiChatResult.Failed(result.detail)
        }
    }

    private suspend fun send(
        sessionId: String,
        messages: List<ChatMessageDto>,
        tools: List<AiTool>?,
    ): SendResult {
        val body = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                topP = config.topP,
                tools = tools?.takeIf { it.isNotEmpty() }?.map { it.toChat() },
                toolChoice = TOOL_CHOICE_AUTO.takeIf { !tools.isNullOrEmpty() },
            ),
        )
        val builder = HttpRequest.newBuilder(URI.create(config.baseUrl))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
        if (sessionId.isNotBlank()) {
            builder.header(SESSION_HEADER, sessionId)
        }
        val httpRequest = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build()

        val attempt = withContext(Dispatchers.IO) {
            runCatching { httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()) }
        }
        val response = attempt.getOrElse { cause ->
            return SendResult.Error(cause.message ?: cause::class.simpleName ?: "request failed")
        }
        if (response.statusCode() !in 200..299) {
            return SendResult.Error("HTTP ${response.statusCode()}: ${response.body().take(ERROR_BODY_SNIPPET)}")
        }

        val choice = runCatching {
            json.decodeFromString(ChatCompletionResponse.serializer(), response.body()).choices?.firstOrNull()
        }.getOrNull() ?: return SendResult.Error("Malformed response from OpenCode")

        val message = choice.message
        val calls = message?.toolCalls.orEmpty().map { AiToolCall(it.id, it.function.name, it.function.arguments) }
        return SendResult.Ok(message?.content, calls, choice.finishReason)
    }
}

private fun AiMessage.toChat(): ChatMessageDto = ChatMessageDto(
    role = role,
    content = content,
    toolCalls = toolCalls.takeIf { it.isNotEmpty() }
        ?.map { ChatToolCallDto(id = it.id, function = ChatFunctionCallDto(it.name, it.arguments)) },
    toolCallId = toolCallId,
)

private fun AiTool.toChat(): ChatToolDto = ChatToolDto(
    function = ChatToolFunctionDto(name = name, description = description, parameters = parameters),
)

private sealed interface SendResult {
    data class Ok(val content: String?, val toolCalls: List<AiToolCall>, val finishReason: String?) : SendResult

    data class Error(val detail: String) : SendResult
}

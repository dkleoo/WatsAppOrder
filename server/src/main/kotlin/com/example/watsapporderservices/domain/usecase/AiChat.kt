package com.example.watsapporderservices.domain.usecase

import kotlinx.serialization.json.JsonObject

/** One chat message of the agent loop (OpenAI-compatible roles: system, user, assistant, tool). */
data class AiMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<AiToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** A function the model may call. [parameters] is a JSON Schema object. */
data class AiTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

sealed interface AiChatResult {
    /** Final assistant text. */
    data class Message(val content: String?) : AiChatResult

    /** The model wants these tools executed before continuing. */
    data class ToolCalls(val calls: List<AiToolCall>) : AiChatResult

    data object NotConfigured : AiChatResult

    data class Failed(val detail: String) : AiChatResult
}

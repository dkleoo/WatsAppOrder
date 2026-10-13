package com.example.watsapporderservices.data.mapper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int,
    @SerialName("top_p") val topP: Double,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    val tools: List<GroqTool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val stream: Boolean = false,
)

@Serializable
internal data class GroqMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<GroqToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class GroqTool(
    val type: String = "function",
    val function: GroqToolFunction,
)

@Serializable
internal data class GroqToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
internal data class GroqToolCall(
    val id: String,
    val type: String = "function",
    val function: GroqFunctionCall,
)

@Serializable
internal data class GroqFunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
internal data class GroqChatResponse(
    val choices: List<GroqChoice>? = null,
)

@Serializable
internal data class GroqChoice(
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: GroqResponseMessage? = null,
)

@Serializable
internal data class GroqResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<GroqToolCall>? = null,
)

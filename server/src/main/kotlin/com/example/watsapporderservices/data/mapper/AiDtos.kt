package com.example.watsapporderservices.data.mapper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double,
    @SerialName("top_p") val topP: Double,
    val tools: List<ChatToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val stream: Boolean = false,
)

@Serializable
internal data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class ChatToolDto(
    val type: String = "function",
    val function: ChatToolFunctionDto,
)

@Serializable
internal data class ChatToolFunctionDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
internal data class ChatToolCallDto(
    val id: String,
    val type: String = "function",
    val function: ChatFunctionCallDto,
)

@Serializable
internal data class ChatFunctionCallDto(
    val name: String,
    val arguments: String,
)

@Serializable
internal data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto>? = null,
)

@Serializable
internal data class ChatChoiceDto(
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: ChatResponseMessageDto? = null,
)

@Serializable
internal data class ChatResponseMessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatToolCallDto>? = null,
)

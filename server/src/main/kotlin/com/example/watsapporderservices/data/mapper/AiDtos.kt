package com.example.watsapporderservices.data.mapper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int,
    @SerialName("top_p") val topP: Double,
    @SerialName("reasoning_effort") val reasoningEffort: String,
    val stream: Boolean = false,
)

@Serializable
internal data class GroqMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class GroqChatResponse(
    val choices: List<GroqChoice>? = null,
)

@Serializable
internal data class GroqChoice(
    val message: GroqResponseMessage? = null,
)

@Serializable
internal data class GroqResponseMessage(
    val role: String? = null,
    val content: String? = null,
)

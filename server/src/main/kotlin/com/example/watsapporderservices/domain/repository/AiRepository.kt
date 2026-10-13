package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.domain.usecase.AiChatResult
import com.example.watsapporderservices.domain.usecase.AiMessage
import com.example.watsapporderservices.domain.usecase.AiTool

interface AiRepository {
    /**
     * One step of a tool-calling conversation: returns the assistant text or the tools to execute.
     * [sessionId] is a stable per-conversation id used by the gateway for routing and prompt caching.
     */
    suspend fun chat(sessionId: String, messages: List<AiMessage>, tools: List<AiTool>): AiChatResult
}

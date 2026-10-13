package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.domain.usecase.AiChatResult
import com.example.watsapporderservices.domain.usecase.AiMessage
import com.example.watsapporderservices.domain.usecase.AiTool

interface AiRepository {
    /** One step of a tool-calling conversation: returns the assistant text or the tools to execute. */
    suspend fun chat(messages: List<AiMessage>, tools: List<AiTool>): AiChatResult
}

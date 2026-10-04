package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.domain.usecase.AiResult

interface AiRepository {
    suspend fun reply(systemPrompt: String, message: String): AiResult
}

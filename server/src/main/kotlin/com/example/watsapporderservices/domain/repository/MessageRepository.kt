package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.SendMessageRequest
import com.example.watsapporderservices.domain.usecase.MessageResult

interface MessageRepository {
    suspend fun send(request: SendMessageRequest): MessageResult
}

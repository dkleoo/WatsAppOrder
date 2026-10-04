package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.SendMessageRequest
import com.example.watsapporderservices.domain.repository.MessageRepository

class MessageUseCase(private val repository: MessageRepository) {
    suspend fun send(request: SendMessageRequest): MessageResult = repository.send(request)
}

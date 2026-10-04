package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.SendMessageRequest
import com.example.watsapporderservices.domain.usecase.MessageResult

interface MessageRepository {
    suspend fun send(request: SendMessageRequest): MessageResult

    /** Sends a text message to a full international number (digits only), without splitting it. */
    suspend fun sendText(to: String, message: String): MessageResult
}

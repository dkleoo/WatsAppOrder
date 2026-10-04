package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.domain.repository.WebhookRepository

class WebhookUseCase(private val repository: WebhookRepository) {
    suspend fun verify(mode: String?, token: String?, challenge: String?): WebhookResult =
        repository.verify(mode, token, challenge)

    suspend fun handleEvent(payload: WhatsAppWebhookPayload) = repository.handleEvent(payload)
}

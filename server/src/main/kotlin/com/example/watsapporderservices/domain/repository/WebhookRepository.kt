package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.domain.usecase.WebhookResult

interface WebhookRepository {
    suspend fun verify(mode: String?, token: String?, challenge: String?): WebhookResult

    suspend fun handleEvent(payload: WhatsAppWebhookPayload)
}

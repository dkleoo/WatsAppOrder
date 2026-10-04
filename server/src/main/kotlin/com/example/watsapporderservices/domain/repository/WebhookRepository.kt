package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.domain.usecase.WebhookResult

interface WebhookRepository {
    suspend fun verify(mode: String?, token: String?, challenge: String?): WebhookResult
}

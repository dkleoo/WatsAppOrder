package com.example.watsapporderservices.domain.usecase

data class WebhookStatus(
    val receivedEvents: Long,
    val receivedMessages: Long,
    val replied: Long,
    val failed: Long,
    val lastActivityEpochMs: Long?,
    val lastError: String?,
)

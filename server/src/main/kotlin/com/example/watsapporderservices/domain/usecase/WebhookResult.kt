package com.example.watsapporderservices.domain.usecase

sealed interface WebhookResult {
    data class Verified(val challenge: String) : WebhookResult

    data object Rejected : WebhookResult
}

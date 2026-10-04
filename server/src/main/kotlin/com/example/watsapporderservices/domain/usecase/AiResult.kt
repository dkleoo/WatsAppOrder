package com.example.watsapporderservices.domain.usecase

sealed interface AiResult {
    data class Reply(val text: String) : AiResult

    data object NotConfigured : AiResult

    data class Failed(val detail: String?) : AiResult
}

package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.MessageErrorCode

sealed interface MessageResult {
    data class Sent(val messageId: String, val to: String) : MessageResult

    data class Invalid(val code: MessageErrorCode) : MessageResult

    data object NotConfigured : MessageResult

    data class ProviderError(val detail: String? = null) : MessageResult
}

package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.OrderResponse

sealed interface OrderResult {
    data class Success(val order: OrderResponse) : OrderResult

    data object NotFound : OrderResult
}

package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.ProductErrorCode
import com.example.watsapporderservices.data.mapper.ProductResponse

sealed interface ProductResult {
    data class Success(val product: ProductResponse) : ProductResult

    data class Invalid(val code: ProductErrorCode) : ProductResult

    data object ProductNotFound : ProductResult

    data object StoreNotFound : ProductResult
}

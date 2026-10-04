package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.StoreErrorCode
import com.example.watsapporderservices.data.mapper.StoreResponse

sealed interface StoreResult {
    data class Success(val store: StoreResponse) : StoreResult

    data class Invalid(val code: StoreErrorCode) : StoreResult

    data object AlreadyExists : StoreResult

    data object NotFound : StoreResult
}

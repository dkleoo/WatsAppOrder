package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.mapper.UserResponse

sealed interface AuthResult {
    data class Success(
        val token: String,
        val expiresIn: Long,
        val user: UserResponse,
    ) : AuthResult

    data class InvalidInput(val code: AuthErrorCode) : AuthResult

    data object EmailAlreadyRegistered : AuthResult

    data object InvalidCredentials : AuthResult

    data object InvalidToken : AuthResult
}

package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.FederatedAuthRequest
import com.example.watsapporderservices.data.mapper.LoginRequest
import com.example.watsapporderservices.data.mapper.RegisterRequest
import com.example.watsapporderservices.data.mapper.UserResponse
import com.example.watsapporderservices.domain.usecase.AuthResult

interface AuthRepository {
    suspend fun register(request: RegisterRequest): AuthResult

    suspend fun login(request: LoginRequest): AuthResult

    suspend fun federated(request: FederatedAuthRequest): AuthResult

    suspend fun profile(userId: Int): UserResponse?

    suspend fun refresh(userId: Int): AuthResult
}

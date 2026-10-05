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

    /**
     * Registers (or re-points) the user's FCM device token, or clears it when [deviceToken] is
     * null/blank. Returns false when the user does not exist.
     */
    suspend fun updateDeviceToken(userId: Int, deviceToken: String?): Boolean
}

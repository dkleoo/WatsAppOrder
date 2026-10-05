package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.FederatedAuthRequest
import com.example.watsapporderservices.data.mapper.LoginRequest
import com.example.watsapporderservices.data.mapper.RegisterRequest
import com.example.watsapporderservices.data.mapper.UserResponse
import com.example.watsapporderservices.domain.repository.AuthRepository

class AuthUseCase(private val repository: AuthRepository) {
    suspend fun register(request: RegisterRequest): AuthResult = repository.register(request)

    suspend fun login(request: LoginRequest): AuthResult = repository.login(request)

    suspend fun federated(request: FederatedAuthRequest): AuthResult = repository.federated(request)

    suspend fun profile(userId: Int): UserResponse? = repository.profile(userId)

    suspend fun refresh(userId: Int): AuthResult = repository.refresh(userId)

    suspend fun updateDeviceToken(userId: Int, deviceToken: String?): Boolean =
        repository.updateDeviceToken(userId, deviceToken)
}

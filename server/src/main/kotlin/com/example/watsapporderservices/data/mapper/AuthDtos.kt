package com.example.watsapporderservices.data.mapper

import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.enum.AuthProvider
import com.example.watsapporderservices.data.database.user.UserEntity
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class FederatedAuthRequest(
    val idToken: String? = null,
    val provider: AuthProvider? = null,
    val name: String? = null,
    /** Optional FCM device token used to register push notifications during the federated login. */
    val deviceToken: String? = null,
)

@Serializable
data class AuthResponse(
    val token: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserResponse,
)

@Serializable
data class UserResponse(
    val id: Int,
    val email: String,
    val name: String,
    val storeId: Int? = null,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

fun UserEntity.toResponse(): UserResponse = UserResponse(
    id = id,
    email = email,
    name = name,
    storeId = storeId,
)

fun AuthErrorCode.toResponse(): ErrorResponse = ErrorResponse(code)

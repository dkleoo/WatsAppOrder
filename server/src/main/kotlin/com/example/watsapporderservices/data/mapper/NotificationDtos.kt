package com.example.watsapporderservices.data.mapper

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    val token: String? = null,
)

@Serializable
data class RegisterDeviceResponse(
    val registered: Boolean,
)

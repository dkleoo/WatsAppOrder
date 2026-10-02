package com.example.watsapporderservices.data.mapper

import com.example.watsapporderservices.data.database.input.InputEntity
import com.example.watsapporderservices.data.enum.InputErrorCode
import kotlinx.serialization.Serializable

@Serializable
data class InputCreateRequest(
    val name: String,
    val price: Double,
    val cost: Double,
    val quantity: Int,
)

@Serializable
data class InputResponse(
    val id: Int,
    val name: String,
    val price: Double,
    val cost: Double,
    val quantity: Int,
)

fun InputEntity.toResponse(): InputResponse = InputResponse(
    id = id,
    name = name,
    price = price.toDouble(),
    cost = cost.toDouble(),
    quantity = quantity,
)

fun InputErrorCode.toResponse(): ErrorResponse = ErrorResponse(code)

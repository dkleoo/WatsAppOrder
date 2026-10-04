package com.example.watsapporderservices.data.mapper

import com.example.watsapporderservices.data.database.input.InputEntity
import com.example.watsapporderservices.data.database.product.ProductEntity
import com.example.watsapporderservices.data.database.step.StepEntity
import com.example.watsapporderservices.data.enum.ProductErrorCode
import com.example.watsapporderservices.data.enum.ProductType
import kotlinx.serialization.Serializable

@Serializable
data class ProductRequest(
    val id: Int? = null,
    val name: String,
    val price: Double,
    val cost: Double,
    val quantity: Int,
    val type: ProductType,
    val steps: List<StepRequest> = emptyList(),
)

@Serializable
data class StepRequest(
    val name: String,
    val position: Int,
    val inputIds: List<Int> = emptyList(),
)

@Serializable
data class ProductResponse(
    val id: Int,
    val name: String,
    val price: Double,
    val cost: Double,
    val quantity: Int,
    val type: ProductType,
    val storeId: Int? = null,
    val steps: List<StepResponse> = emptyList(),
)

@Serializable
data class StepResponse(
    val id: Int,
    val productId: Int,
    val name: String,
    val position: Int,
    val inputs: List<InputResponse> = emptyList(),
)

fun ProductEntity.toResponse(
    steps: List<StepEntity>,
    inputsByStep: Map<Int, List<InputEntity>>,
): ProductResponse = ProductResponse(
    id = id,
    name = name,
    price = price.toDouble(),
    cost = cost.toDouble(),
    quantity = quantity,
    type = type,
    storeId = storeId,
    steps = steps.map { it.toResponse(inputsByStep[it.id].orEmpty()) },
)

fun StepEntity.toResponse(inputs: List<InputEntity>): StepResponse = StepResponse(
    id = id,
    productId = productId,
    name = name,
    position = position,
    inputs = inputs.map { it.toResponse() },
)

fun ProductErrorCode.toResponse(): ErrorResponse = ErrorResponse(code)

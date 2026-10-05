package com.example.watsapporderservices.data.mapper

import com.example.watsapporderservices.data.enum.OrderErrorCode
import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.domain.usecase.OrderDraft
import com.example.watsapporderservices.domain.usecase.OrderItemDraft
import kotlinx.serialization.Serializable

@Serializable
data class OrderItemResponse(
    val id: Int,
    val productId: Int? = null,
    val productName: String,
    val unitPrice: Double,
    val stepName: String? = null,
    val selectedInputIds: List<Int> = emptyList(),
    val quantity: Int,
    val subtotal: Double,
)

@Serializable
data class OrderResponse(
    val id: Int,
    val sequence: Long,
    val storeId: Int? = null,
    val customerPhone: String,
    val customerName: String? = null,
    val deliveryAddress: String? = null,
    val paymentType: String? = null,
    val total: Double? = null,
    val status: OrderStatus,
    val items: List<OrderItemResponse> = emptyList(),
)

@Serializable
data class UpdateOrderStatusRequest(
    val status: OrderStatus,
)

/** A resolved option chosen within a step (id, display name and its extra price). */
@Serializable
data class OrderItemInputResponse(
    val id: Int,
    val name: String,
    val price: Double,
)

/** The options chosen for one of the product's steps (e.g. "ENTRADA (SOPA)"). */
@Serializable
data class OrderItemStepResponse(
    val stepId: Int? = null,
    val name: String,
    val position: Int = 0,
    val inputs: List<OrderItemInputResponse> = emptyList(),
)

@Serializable
data class OrderDetailItemResponse(
    val id: Int,
    val productId: Int? = null,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    val subtotal: Double,
    val steps: List<OrderItemStepResponse> = emptyList(),
)

@Serializable
data class OrderDetailResponse(
    val id: Int,
    val sequence: Long,
    val storeId: Int? = null,
    val customerPhone: String,
    val customerName: String? = null,
    val deliveryAddress: String? = null,
    val paymentType: String? = null,
    val total: Double? = null,
    val status: OrderStatus,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val items: List<OrderDetailItemResponse> = emptyList(),
)

fun OrderDraft.toResponse(): OrderResponse = OrderResponse(
    id = id ?: 0,
    sequence = sequence ?: 0L,
    storeId = storeId,
    customerPhone = customerPhone,
    customerName = customerName,
    deliveryAddress = deliveryAddress,
    paymentType = paymentType,
    total = total,
    status = status,
    items = items.map { it.toResponse() },
)

fun OrderItemDraft.toResponse(): OrderItemResponse = OrderItemResponse(
    id = id ?: 0,
    productId = productId,
    productName = productName,
    unitPrice = unitPrice,
    stepName = stepName,
    selectedInputIds = selectedInputIds,
    quantity = quantity,
    subtotal = unitPrice * quantity,
)

fun OrderErrorCode.toResponse(): ErrorResponse = ErrorResponse(code)

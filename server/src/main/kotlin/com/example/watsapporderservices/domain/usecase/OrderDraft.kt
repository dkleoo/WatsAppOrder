package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.enum.OrderType
import com.example.watsapporderservices.domain.usecase.OrderItemDraft

data class OrderDraft(
    val id: Int?,
    val sequence: Long? = null,
    val storeId: Int?,
    val customerPhone: String,
    val customerName: String?,
    val deliveryAddress: String?,
    val paymentType: String?,
    val total: Double?,
    val status: OrderStatus,
    val orderType: OrderType? = null,
    val items: List<OrderItemDraft> = emptyList(),
)

data class OrderItemDraft(
    val id: Int?,
    val productId: Int?,
    val productName: String,
    val unitPrice: Double,
    val selectedInputIds: List<Int> = emptyList(),
    val stepName: String? = null,
    val quantity: Int = 1,
)

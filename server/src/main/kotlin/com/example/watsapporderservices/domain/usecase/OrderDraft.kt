package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.OrderStatus

data class OrderDraft(
    val id: Int?,
    val storeId: Int?,
    val customerPhone: String,
    val productId: Int?,
    val productName: String?,
    val unitPrice: Double?,
    val selectedInputIds: List<Int>,
    val quantity: Int?,
    val total: Double?,
    val customerName: String?,
    val deliveryAddress: String?,
    val paymentType: String?,
    val status: OrderStatus,
)

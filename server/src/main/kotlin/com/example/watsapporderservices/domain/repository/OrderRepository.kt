package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.domain.usecase.OrderDraft

interface OrderRepository {
    suspend fun createDraft(storeId: Int?, customerPhone: String): OrderDraft

    suspend fun getOrder(id: Int): OrderDraft?

    suspend fun save(order: OrderDraft): OrderDraft
}

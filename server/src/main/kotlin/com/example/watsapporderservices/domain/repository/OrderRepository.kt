package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.mapper.OrderDetailResponse
import com.example.watsapporderservices.data.mapper.OrderResponse
import com.example.watsapporderservices.domain.usecase.OrderDraft
import com.example.watsapporderservices.domain.usecase.OrderItemDraft

interface OrderRepository {
    suspend fun createDraft(storeId: Int?, customerPhone: String): OrderDraft

    suspend fun getOrder(id: Int): OrderDraft?

    suspend fun save(order: OrderDraft): OrderDraft

    /** Orders of a store, newest first. When [statuses] is not empty only those statuses are returned. */
    suspend fun getOrders(storeId: Int, statuses: List<OrderStatus> = emptyList()): List<OrderResponse>

    /** One order with its lines, options resolved to names and grouped by step. */
    suspend fun getOrderDetail(id: Int): OrderDetailResponse?

    /** Highest stored sequence (0 when there are no orders). Used by clients to know where they are. */
    suspend fun maxSequence(): Long

    /** Orders of a store with sequence greater than [sinceSequence], ascending. */
    suspend fun getOrdersSince(storeId: Int, sinceSequence: Long): List<OrderResponse>

    suspend fun updateStatus(id: Int, status: OrderStatus): OrderResponse?

    /** Adds a line to the order and returns the persisted line (with its id). */
    suspend fun addItem(orderId: Int, item: OrderItemDraft): OrderItemDraft

    /** Replaces the lines of the order (used to update a configured line). */
    suspend fun updateItem(item: OrderItemDraft): OrderItemDraft
}

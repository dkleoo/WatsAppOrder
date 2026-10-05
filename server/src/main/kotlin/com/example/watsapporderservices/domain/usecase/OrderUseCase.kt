package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.mapper.OrderDetailResponse
import com.example.watsapporderservices.data.mapper.OrderResponse
import com.example.watsapporderservices.domain.repository.OrderRepository
import com.example.watsapporderservices.domain.repository.StoreRepository

class OrderUseCase(
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
) {
    /**
     * Orders of the authenticated user's store, all statuses by default. Empty if the user has no store.
     * When [statuses] is not empty, only orders in those statuses are returned.
     */
    suspend fun getOrders(userId: Int, statuses: List<OrderStatus> = emptyList()): List<OrderResponse> {
        val store = storeRepository.getStoreByUser(userId) ?: return emptyList()
        return orderRepository.getOrders(store.id, statuses)
    }

    /**
     * Full detail of one order, only if it belongs to the authenticated user's store (null otherwise,
     * so callers can answer 404 without leaking that the order exists).
     */
    suspend fun getOrderDetail(userId: Int, orderId: Int): OrderDetailResponse? {
        val store = storeRepository.getStoreByUser(userId) ?: return null
        val order = orderRepository.getOrderDetail(orderId) ?: return null
        return order.takeIf { it.storeId == store.id }
    }

    /** Global highest sequence; clients use it to know where the stream is. */
    suspend fun currentSequence(): Long = orderRepository.maxSequence()

    /** Orders of the user's store created after [sinceSequence] (catch-up after reconnect). */
    suspend fun getOrdersSince(userId: Int, sinceSequence: Long): List<OrderResponse> {
        val store = storeRepository.getStoreByUser(userId) ?: return emptyList()
        return orderRepository.getOrdersSince(store.id, sinceSequence)
    }

    suspend fun storeIdOf(userId: Int): Int? = storeRepository.getStoreByUser(userId)?.id

    /** Changes an order's status, only if the order belongs to the authenticated user's store. */
    suspend fun updateStatus(userId: Int, id: Int, status: OrderStatus): OrderResult {
        val store = storeRepository.getStoreByUser(userId) ?: return OrderResult.NotFound
        val existing = orderRepository.getOrderDetail(id) ?: return OrderResult.NotFound
        if (existing.storeId != store.id) return OrderResult.NotFound
        val order = orderRepository.updateStatus(id, status) ?: return OrderResult.NotFound
        return OrderResult.Success(order)
    }
}

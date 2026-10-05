package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.mapper.OrderResponse
import com.example.watsapporderservices.domain.repository.OrderRepository
import com.example.watsapporderservices.domain.repository.StoreRepository

class OrderUseCase(
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
) {
    /** Orders of the authenticated user's store. Empty if the user has no store. */
    suspend fun getOrders(userId: Int): List<OrderResponse> {
        val store = storeRepository.getStoreByUser(userId) ?: return emptyList()
        return orderRepository.getOrders(store.id)
    }

    /** Global highest sequence; clients use it to know where the stream is. */
    suspend fun currentSequence(): Long = orderRepository.maxSequence()

    /** Orders of the user's store created after [sinceSequence] (catch-up after reconnect). */
    suspend fun getOrdersSince(userId: Int, sinceSequence: Long): List<OrderResponse> {
        val store = storeRepository.getStoreByUser(userId) ?: return emptyList()
        return orderRepository.getOrdersSince(store.id, sinceSequence)
    }

    suspend fun storeIdOf(userId: Int): Int? = storeRepository.getStoreByUser(userId)?.id

    suspend fun updateStatus(id: Int, status: OrderStatus): OrderResult {
        val order = orderRepository.updateStatus(id, status) ?: return OrderResult.NotFound
        return OrderResult.Success(order)
    }
}

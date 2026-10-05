package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.order.OrderDao
import com.example.watsapporderservices.data.database.order.OrderEntity
import com.example.watsapporderservices.data.database.order.OrderItemDao
import com.example.watsapporderservices.data.database.order.OrderItemEntity
import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.mapper.OrderResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.repository.OrderRepository
import com.example.watsapporderservices.domain.usecase.OrderDraft
import com.example.watsapporderservices.domain.usecase.OrderItemDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class OrderRepositoryImpl(
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
) : OrderRepository {
    override suspend fun createDraft(storeId: Int?, customerPhone: String): OrderDraft =
        withContext(Dispatchers.IO) {
            orderDao.insert(storeId, customerPhone, System.currentTimeMillis()).toDraft(emptyList())
        }

    override suspend fun getOrder(id: Int): OrderDraft? = withContext(Dispatchers.IO) {
        val order = orderDao.findById(id) ?: return@withContext null
        order.toDraft(orderItemDao.findByOrderId(id).map { it.toDraft() })
    }

    override suspend fun getOrders(storeId: Int): List<OrderResponse> = withContext(Dispatchers.IO) {
        orderDao.findByStoreId(storeId).map { order ->
            order.toDraft(orderItemDao.findByOrderId(order.id).map { it.toDraft() }).toResponse()
        }
    }

    override suspend fun maxSequence(): Long = withContext(Dispatchers.IO) {
        orderDao.maxSequence()
    }

    override suspend fun getOrdersSince(storeId: Int, sinceSequence: Long): List<OrderResponse> =
        withContext(Dispatchers.IO) {
            orderDao.findByStoreIdSince(storeId, sinceSequence).map { order ->
                order.toDraft(orderItemDao.findByOrderId(order.id).map { it.toDraft() }).toResponse()
            }
        }

    override suspend fun updateStatus(id: Int, status: OrderStatus): OrderResponse? = withContext(Dispatchers.IO) {
        if (orderDao.findById(id) == null) return@withContext null
        orderDao.updateStatus(id, status, System.currentTimeMillis())
        val updated = orderDao.findById(id) ?: return@withContext null
        updated.toDraft(orderItemDao.findByOrderId(id).map { it.toDraft() }).toResponse()
    }

    override suspend fun save(order: OrderDraft): OrderDraft = withContext(Dispatchers.IO) {
        val entity = order.toEntity()
        val now = System.currentTimeMillis()
        if (entity.id == 0) {
            order
        } else {
            orderDao.update(entity, now)
            entity.copy(updatedAt = now).toDraft(order.items)
        }
    }

    override suspend fun addItem(orderId: Int, item: OrderItemDraft): OrderItemDraft = withContext(Dispatchers.IO) {
        orderItemDao.insert(
            orderId = orderId,
            productId = item.productId,
            productName = item.productName,
            unitPrice = BigDecimal.valueOf(item.unitPrice),
            selectedInputIds = item.selectedInputIds,
            stepName = item.stepName,
            quantity = item.quantity,
        ).toDraft()
    }

    override suspend fun updateItem(item: OrderItemDraft): OrderItemDraft = withContext(Dispatchers.IO) {
        val entity = OrderItemEntity(
            id = item.id ?: 0,
            orderId = 0,
            productId = item.productId,
            productName = item.productName,
            unitPrice = BigDecimal.valueOf(item.unitPrice),
            selectedInputIds = item.selectedInputIds,
            stepName = item.stepName,
            quantity = item.quantity,
        )
        orderItemDao.update(entity)
        item
    }
}

private fun OrderEntity.toDraft(items: List<OrderItemDraft>): OrderDraft = OrderDraft(
    id = id,
    sequence = sequence,
    storeId = storeId,
    customerPhone = customerPhone,
    customerName = customerName,
    deliveryAddress = deliveryAddress,
    paymentType = paymentType,
    total = total?.toDouble(),
    status = status,
    items = items,
)

private fun OrderItemEntity.toDraft(): OrderItemDraft = OrderItemDraft(
    id = id,
    productId = productId,
    productName = productName,
    unitPrice = unitPrice.toDouble(),
    selectedInputIds = selectedInputIds,
    stepName = stepName,
    quantity = quantity,
)

private fun OrderDraft.toEntity(): OrderEntity = OrderEntity(
    id = id ?: 0,
    sequence = sequence ?: 0L,
    storeId = storeId,
    customerPhone = customerPhone,
    customerName = customerName,
    deliveryAddress = deliveryAddress,
    paymentType = paymentType,
    total = total?.let { BigDecimal.valueOf(it) },
    status = status,
)

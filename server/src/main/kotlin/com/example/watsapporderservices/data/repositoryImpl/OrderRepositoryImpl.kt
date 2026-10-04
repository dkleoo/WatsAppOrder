package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.order.OrderDao
import com.example.watsapporderservices.data.database.order.OrderEntity
import com.example.watsapporderservices.domain.repository.OrderRepository
import com.example.watsapporderservices.domain.usecase.OrderDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class OrderRepositoryImpl(
    private val orderDao: OrderDao,
) : OrderRepository {
    override suspend fun createDraft(storeId: Int?, customerPhone: String): OrderDraft =
        withContext(Dispatchers.IO) {
            orderDao.insert(storeId, customerPhone, System.currentTimeMillis()).toDraft()
        }

    override suspend fun getOrder(id: Int): OrderDraft? = withContext(Dispatchers.IO) {
        orderDao.findById(id)?.toDraft()
    }

    override suspend fun save(order: OrderDraft): OrderDraft = withContext(Dispatchers.IO) {
        val entity = order.toEntity()
        if (entity.id == 0) {
            order
        } else {
            orderDao.update(entity, System.currentTimeMillis())
            entity.copy(updatedAt = System.currentTimeMillis()).toDraft()
        }
    }
}

private fun OrderEntity.toDraft(): OrderDraft = OrderDraft(
    id = id,
    storeId = storeId,
    customerPhone = customerPhone,
    productId = productId,
    productName = productName,
    unitPrice = unitPrice?.toDouble(),
    selectedInputIds = selectedInputIds,
    quantity = quantity,
    total = total?.toDouble(),
    customerName = customerName,
    deliveryAddress = deliveryAddress,
    paymentType = paymentType,
    status = status,
)

private fun OrderDraft.toEntity(): OrderEntity = OrderEntity(
    id = id ?: 0,
    storeId = storeId,
    customerPhone = customerPhone,
    productId = productId,
    productName = productName,
    unitPrice = unitPrice?.let { BigDecimal.valueOf(it) },
    selectedInputIds = selectedInputIds,
    quantity = quantity,
    total = total?.let { BigDecimal.valueOf(it) },
    customerName = customerName,
    deliveryAddress = deliveryAddress,
    paymentType = paymentType,
    status = status,
)

package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.input.InputDao
import com.example.watsapporderservices.data.database.input.InputEntity
import com.example.watsapporderservices.data.database.order.OrderDao
import com.example.watsapporderservices.data.database.order.OrderEntity
import com.example.watsapporderservices.data.database.order.OrderItemDao
import com.example.watsapporderservices.data.database.order.OrderItemEntity
import com.example.watsapporderservices.data.database.step.StepDao
import com.example.watsapporderservices.data.database.step.StepInputDao
import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.mapper.OrderDetailItemResponse
import com.example.watsapporderservices.data.mapper.OrderDetailResponse
import com.example.watsapporderservices.data.mapper.OrderItemInputResponse
import com.example.watsapporderservices.data.mapper.OrderItemStepResponse
import com.example.watsapporderservices.data.mapper.OrderResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.repository.OrderRepository
import com.example.watsapporderservices.domain.usecase.OrderDraft
import com.example.watsapporderservices.domain.usecase.OrderItemDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal

class OrderRepositoryImpl(
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
    private val stepDao: StepDao,
    private val stepInputDao: StepInputDao,
    private val inputDao: InputDao,
) : OrderRepository {
    override suspend fun createDraft(storeId: Int?, customerPhone: String): OrderDraft =
        withContext(Dispatchers.IO) {
            orderDao.insert(storeId, customerPhone, System.currentTimeMillis()).toDraft(emptyList())
        }

    override suspend fun getOrder(id: Int): OrderDraft? = withContext(Dispatchers.IO) {
        val order = orderDao.findById(id) ?: return@withContext null
        order.toDraft(orderItemDao.findByOrderId(id).map { it.toDraft() })
    }

    override suspend fun getOrders(storeId: Int, statuses: List<OrderStatus>): List<OrderResponse> =
        withContext(Dispatchers.IO) {
            orderDao.findByStoreId(storeId, statuses).map { order ->
                order.toDraft(orderItemDao.findByOrderId(order.id).map { it.toDraft() }).toResponse()
            }
        }

    override suspend fun getOrderDetail(id: Int): OrderDetailResponse? = withContext(Dispatchers.IO) {
        transaction {
            val order = orderDao.findById(id) ?: return@transaction null
            val items = orderItemDao.findByOrderId(id).map { buildItemDetail(it) }
            order.toDetailResponse(items)
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

    /**
     * Resolves an order line's chosen options to names/prices and groups them by the product's steps,
     * so the UI can render e.g. "ENTRADA (SOPA): Sopa Marinera Especial +$2.00".
     */
    private fun buildItemDetail(item: OrderItemEntity): OrderDetailItemResponse {
        val inputsById = inputDao.findByIds(item.selectedInputIds).associateBy { it.id }
        val steps = item.productId?.let { resolveSteps(it, item, inputsById) }.orEmpty()
        return OrderDetailItemResponse(
            id = item.id,
            productId = item.productId,
            productName = item.productName,
            unitPrice = item.unitPrice.toDouble(),
            quantity = item.quantity,
            subtotal = item.unitPrice.toDouble() * item.quantity,
            steps = steps,
        )
    }

    private fun resolveSteps(
        productId: Int,
        item: OrderItemEntity,
        inputsById: Map<Int, InputEntity>,
    ): List<OrderItemStepResponse> {
        val steps = stepDao.findByProductId(productId)
        val inputsByStep = stepInputDao.findByStepIds(steps.map { it.id })
            .groupBy({ it.stepId }, { it.inputId })
        val matched = mutableSetOf<Int>()
        val grouped = steps.mapNotNull { step ->
            val chosen = inputsByStep[step.id].orEmpty()
                .filter { it in item.selectedInputIds }
                .mapNotNull { id -> inputsById[id] }
            if (chosen.isEmpty()) return@mapNotNull null
            matched += chosen.map { it.id }
            OrderItemStepResponse(
                stepId = step.id,
                name = step.name,
                position = step.position,
                inputs = chosen.map { it.toDetailInput() },
            )
        }
        // Options whose step no longer exists (product edited/deleted) are kept so nothing is lost.
        val leftovers = item.selectedInputIds.filterNot { it in matched }.mapNotNull { inputsById[it] }
        if (leftovers.isEmpty()) return grouped
        return grouped + OrderItemStepResponse(
            stepId = 0,
            name = item.stepName ?: "Opciones",
            position = grouped.size,
            inputs = leftovers.map { it.toDetailInput() },
        )
    }
}

private fun InputEntity.toDetailInput(): OrderItemInputResponse = OrderItemInputResponse(
    id = id,
    name = name,
    price = price.toDouble(),
)

private fun OrderEntity.toDetailResponse(items: List<OrderDetailItemResponse>): OrderDetailResponse = OrderDetailResponse(
    id = id,
    sequence = sequence,
    storeId = storeId,
    customerPhone = customerPhone,
    customerName = customerName,
    deliveryAddress = deliveryAddress,
    paymentType = paymentType,
    total = total?.toDouble(),
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items,
)

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

package com.example.watsapporderservices.data.database.order

import com.example.watsapporderservices.data.enum.OrderStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class OrderDao {
    fun findById(id: Int): OrderEntity? = transaction {
        Orders.selectAll().where { Orders.id eq id }.singleOrNull()?.toEntity()
    }

    fun insert(storeId: Int?, customerPhone: String, now: Long): OrderEntity = transaction {
        val id = Orders.insert {
            it[Orders.storeId] = storeId
            it[Orders.customerPhone] = customerPhone
            it[Orders.status] = OrderStatus.DRAFT
            it[Orders.createdAt] = now
            it[Orders.updatedAt] = now
        } get Orders.id
        OrderEntity(id, storeId, customerPhone, null, null, null, emptyList(), null, null, null, null, null, OrderStatus.DRAFT, now, now)
    }

    fun update(order: OrderEntity, now: Long): Int = transaction {
        Orders.update({ Orders.id eq order.id }) {
            it[storeId] = order.storeId
            it[customerPhone] = order.customerPhone
            it[productId] = order.productId
            it[productName] = order.productName
            it[unitPrice] = order.unitPrice
            it[selectedInputs] = encodeInputs(order.selectedInputIds)
            it[quantity] = order.quantity
            it[total] = order.total
            it[customerName] = order.customerName
            it[deliveryAddress] = order.deliveryAddress
            it[paymentType] = order.paymentType
            it[status] = order.status
            it[updatedAt] = now
        }
    }
}

private fun ResultRow.toEntity(): OrderEntity = OrderEntity(
    id = this[Orders.id],
    storeId = this[Orders.storeId],
    customerPhone = this[Orders.customerPhone],
    productId = this[Orders.productId],
    productName = this[Orders.productName],
    unitPrice = this[Orders.unitPrice],
    selectedInputIds = decodeInputs(this[Orders.selectedInputs]),
    quantity = this[Orders.quantity],
    total = this[Orders.total],
    customerName = this[Orders.customerName],
    deliveryAddress = this[Orders.deliveryAddress],
    paymentType = this[Orders.paymentType],
    status = this[Orders.status],
    createdAt = this[Orders.createdAt],
    updatedAt = this[Orders.updatedAt],
)

private fun encodeInputs(ids: List<Int>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString(",")

private fun decodeInputs(value: String?): List<Int> =
    value?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

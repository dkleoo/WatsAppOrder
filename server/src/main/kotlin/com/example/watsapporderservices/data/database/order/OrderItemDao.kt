package com.example.watsapporderservices.data.database.order

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal

class OrderItemDao {
    fun findByOrderId(orderId: Int): List<OrderItemEntity> = transaction {
        OrderItems.selectAll().where { OrderItems.orderId eq orderId }.orderBy(OrderItems.id).map { it.toEntity() }
    }

    fun insert(
        orderId: Int,
        productId: Int?,
        productName: String,
        unitPrice: BigDecimal,
        selectedInputIds: List<Int>,
        stepName: String?,
        quantity: Int,
    ): OrderItemEntity = transaction {
        val id = OrderItems.insert {
            it[OrderItems.orderId] = orderId
            it[OrderItems.productId] = productId
            it[OrderItems.productName] = productName
            it[OrderItems.unitPrice] = unitPrice
            it[OrderItems.selectedInputs] = encodeIds(selectedInputIds)
            it[OrderItems.stepName] = stepName
            it[OrderItems.quantity] = quantity
        } get OrderItems.id
        OrderItemEntity(id, orderId, productId, productName, unitPrice, selectedInputIds, stepName, quantity)
    }

    /** Updates the quantity and options of an existing line (used when the client configures a product). */
    fun update(item: OrderItemEntity): Int = transaction {
        OrderItems.update({ OrderItems.id eq item.id }) {
            it[selectedInputs] = encodeIds(item.selectedInputIds)
            it[stepName] = item.stepName
            it[quantity] = item.quantity
        }
    }

    fun deleteByOrderId(orderId: Int) = transaction {
        OrderItems.deleteWhere { OrderItems.orderId eq orderId }
    }
}

private fun ResultRow.toEntity(): OrderItemEntity = OrderItemEntity(
    id = this[OrderItems.id],
    orderId = this[OrderItems.orderId],
    productId = this[OrderItems.productId],
    productName = this[OrderItems.productName],
    unitPrice = this[OrderItems.unitPrice],
    selectedInputIds = decodeIds(this[OrderItems.selectedInputs]),
    stepName = this[OrderItems.stepName],
    quantity = this[OrderItems.quantity],
)

private fun encodeIds(ids: List<Int>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString(",")

private fun decodeIds(value: String?): List<Int> =
    value?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

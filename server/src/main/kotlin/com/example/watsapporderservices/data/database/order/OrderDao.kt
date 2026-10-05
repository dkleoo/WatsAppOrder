package com.example.watsapporderservices.data.database.order

import com.example.watsapporderservices.data.enum.OrderStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class OrderDao {
    fun findById(id: Int): OrderEntity? = transaction {
        Orders.selectAll().where { Orders.id eq id }.singleOrNull()?.toEntity()
    }

    /** Orders of a store, newest first. Empty [statuses] means "all statuses". */
    fun findByStoreId(storeId: Int, statuses: List<OrderStatus> = emptyList()): List<OrderEntity> = transaction {
        val rows = if (statuses.isEmpty()) {
            Orders.selectAll().where { Orders.storeId eq storeId }
        } else {
            Orders.selectAll().where { (Orders.storeId eq storeId) and (Orders.status inList statuses) }
        }
        rows.orderBy(Orders.sequence, SortOrder.DESC).map { it.toEntity() }
    }

    fun findAll(): List<OrderEntity> = transaction {
        Orders.selectAll().orderBy(Orders.sequence, SortOrder.DESC).map { it.toEntity() }
    }

    /** Orders of a store with sequence greater than [sinceSequence] (to catch up after reconnect). */
    fun findByStoreIdSince(storeId: Int, sinceSequence: Long): List<OrderEntity> = transaction {
        Orders.selectAll()
            .where { (Orders.storeId eq storeId) and (Orders.sequence greater sinceSequence) }
            .orderBy(Orders.sequence, SortOrder.ASC)
            .map { it.toEntity() }
    }

    /** Highest sequence currently stored (0 when there are no orders). */
    fun maxSequence(): Long = transaction {
        Orders.select(Orders.sequence).orderBy(Orders.sequence, SortOrder.DESC).limit(1)
            .firstOrNull()?.get(Orders.sequence) ?: 0L
    }

    fun updateStatus(id: Int, status: OrderStatus, now: Long): Int = transaction {
        Orders.update({ Orders.id eq id }) {
            it[Orders.status] = status
            it[Orders.updatedAt] = now
        }
    }

    fun insert(storeId: Int?, customerPhone: String, now: Long): OrderEntity = transaction {
        val id = Orders.insert {
            it[Orders.storeId] = storeId
            it[Orders.customerPhone] = customerPhone
            it[Orders.status] = OrderStatus.DRAFT
            it[Orders.createdAt] = now
            it[Orders.updatedAt] = now
        } get Orders.id
        val created = findById(id) ?: error("Order $id was not found after insert")
        created
    }

    fun update(order: OrderEntity, now: Long): Int = transaction {
        Orders.update({ Orders.id eq order.id }) {
            it[storeId] = order.storeId
            it[customerPhone] = order.customerPhone
            it[customerName] = order.customerName
            it[deliveryAddress] = order.deliveryAddress
            it[paymentType] = order.paymentType
            it[total] = order.total
            it[status] = order.status
            it[updatedAt] = now
        }
    }
}

private fun ResultRow.toEntity(): OrderEntity = OrderEntity(
    id = this[Orders.id],
    sequence = this[Orders.sequence],
    storeId = this[Orders.storeId],
    customerPhone = this[Orders.customerPhone],
    customerName = this[Orders.customerName],
    deliveryAddress = this[Orders.deliveryAddress],
    paymentType = this[Orders.paymentType],
    total = this[Orders.total],
    status = this[Orders.status],
    createdAt = this[Orders.createdAt],
    updatedAt = this[Orders.updatedAt],
)

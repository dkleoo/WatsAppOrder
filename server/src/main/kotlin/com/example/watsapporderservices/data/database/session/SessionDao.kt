package com.example.watsapporderservices.data.database.session

import com.example.watsapporderservices.data.enum.SessionState
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class SessionDao {
    fun findByCustomerPhone(customerPhone: String): SessionEntity? = transaction {
        Sessions.selectAll().where { Sessions.customerPhone eq customerPhone }.singleOrNull()?.toEntity()
    }

    fun insert(
        customerPhone: String,
        storeId: Int?,
        orderId: Int?,
        state: SessionState,
        optionProductIds: List<Int>,
        lastActivityAt: Long,
    ): SessionEntity = transaction {
        val id = Sessions.insert {
            it[Sessions.customerPhone] = customerPhone
            it[Sessions.storeId] = storeId
            it[Sessions.orderId] = orderId
            it[Sessions.state] = state
            it[Sessions.optionIds] = encodeOptions(optionProductIds)
            it[Sessions.lastActivityAt] = lastActivityAt
        } get Sessions.id
        SessionEntity(id, customerPhone, storeId, orderId, state, optionProductIds, lastActivityAt)
    }

    fun update(
        id: Int,
        storeId: Int?,
        orderId: Int?,
        state: SessionState,
        optionProductIds: List<Int>,
        lastActivityAt: Long,
    ): Int = transaction {
        Sessions.update({ Sessions.id eq id }) {
            it[Sessions.storeId] = storeId
            it[Sessions.orderId] = orderId
            it[Sessions.state] = state
            it[Sessions.optionIds] = encodeOptions(optionProductIds)
            it[Sessions.lastActivityAt] = lastActivityAt
        }
    }
}

private fun ResultRow.toEntity(): SessionEntity = SessionEntity(
    id = this[Sessions.id],
    customerPhone = this[Sessions.customerPhone],
    storeId = this[Sessions.storeId],
    orderId = this[Sessions.orderId],
    state = this[Sessions.state],
    optionProductIds = decodeOptions(this[Sessions.optionIds]),
    lastActivityAt = this[Sessions.lastActivityAt],
)

private fun encodeOptions(ids: List<Int>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString(",")

private fun decodeOptions(value: String?): List<Int> =
    value?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

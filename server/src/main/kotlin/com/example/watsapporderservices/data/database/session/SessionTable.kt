package com.example.watsapporderservices.data.database.session

import com.example.watsapporderservices.data.database.order.Orders
import com.example.watsapporderservices.data.database.store.Stores
import com.example.watsapporderservices.data.enum.SessionState
import org.jetbrains.exposed.v1.core.Table

const val CUSTOMER_PHONE_MAX_LENGTH = 40
const val SESSION_STATE_MAX_LENGTH = 30
const val SESSION_OPTIONS_MAX_LENGTH = 2000

object Sessions : Table("sessions") {
    val id = integer("id").autoIncrement()
    val customerPhone = varchar("customer_phone", CUSTOMER_PHONE_MAX_LENGTH).uniqueIndex()
    val storeId = integer("store_id").references(Stores.id).nullable()
    val orderId = integer("order_id").references(Orders.id).nullable()
    val state = enumerationByName("state", SESSION_STATE_MAX_LENGTH, SessionState::class)
    val optionIds = varchar("options", SESSION_OPTIONS_MAX_LENGTH).nullable()
    // Which step of the current product is being configured (0-based).
    val stepIndex = integer("step_index").default(0)
    val lastActivityAt = long("last_activity_at")

    override val primaryKey = PrimaryKey(id)
}

data class SessionEntity(
    val id: Int,
    val customerPhone: String,
    val storeId: Int?,
    val orderId: Int?,
    val state: SessionState,
    val optionProductIds: List<Int>,
    val stepIndex: Int,
    val lastActivityAt: Long,
)

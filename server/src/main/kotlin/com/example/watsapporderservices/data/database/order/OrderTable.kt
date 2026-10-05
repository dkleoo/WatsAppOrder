package com.example.watsapporderservices.data.database.order

import com.example.watsapporderservices.data.database.product.MONEY_PRECISION
import com.example.watsapporderservices.data.database.product.MONEY_SCALE
import com.example.watsapporderservices.data.database.store.Stores
import com.example.watsapporderservices.data.enum.OrderStatus
import org.jetbrains.exposed.v1.core.Table
import java.math.BigDecimal

const val ORDER_CUSTOMER_PHONE_MAX_LENGTH = 40
const val ORDER_CUSTOMER_NAME_MAX_LENGTH = 160
const val ORDER_ADDRESS_MAX_LENGTH = 300
const val ORDER_PAYMENT_MAX_LENGTH = 60
const val ORDER_STATUS_MAX_LENGTH = 20

/** Order header. Lines live in `order_items`. */
object Orders : Table("orders") {
    val id = integer("id").autoIncrement()
    // Monotonic sequence for real-time delivery: the websocket sends it and clients resume with it.
    val sequence = long("sequence").autoIncrement()
    val storeId = integer("store_id").references(Stores.id).nullable()
    val customerPhone = varchar("customer_phone", ORDER_CUSTOMER_PHONE_MAX_LENGTH)
    val customerName = varchar("customer_name", ORDER_CUSTOMER_NAME_MAX_LENGTH).nullable()
    val deliveryAddress = varchar("delivery_address", ORDER_ADDRESS_MAX_LENGTH).nullable()
    val paymentType = varchar("payment_type", ORDER_PAYMENT_MAX_LENGTH).nullable()
    val total = decimal("total", MONEY_PRECISION, MONEY_SCALE).nullable()
    val status = enumerationByName("status", ORDER_STATUS_MAX_LENGTH, OrderStatus::class)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

data class OrderEntity(
    val id: Int,
    val sequence: Long,
    val storeId: Int?,
    val customerPhone: String,
    val customerName: String?,
    val deliveryAddress: String?,
    val paymentType: String?,
    val total: BigDecimal?,
    val status: OrderStatus,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

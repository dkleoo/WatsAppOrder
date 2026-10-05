package com.example.watsapporderservices.data.database.order

import com.example.watsapporderservices.data.database.product.MONEY_PRECISION
import com.example.watsapporderservices.data.database.product.MONEY_SCALE
import com.example.watsapporderservices.data.database.product.PRODUCT_NAME_MAX_LENGTH
import com.example.watsapporderservices.data.database.product.Products
import org.jetbrains.exposed.v1.core.Table
import java.math.BigDecimal

const val ORDER_ITEM_STEP_MAX_LENGTH = 160
const val ORDER_ITEM_OPTIONS_MAX_LENGTH = 2000

object OrderItems : Table("order_items") {
    val id = integer("id").autoIncrement()
    val orderId = integer("order_id").references(Orders.id)
    val productId = integer("product_id").references(Products.id).nullable()
    val productName = varchar("product_name", PRODUCT_NAME_MAX_LENGTH)
    val unitPrice = decimal("unit_price", MONEY_PRECISION, MONEY_SCALE)
    val selectedInputs = varchar("selected_inputs", ORDER_ITEM_OPTIONS_MAX_LENGTH).nullable()
    val stepName = varchar("step_name", ORDER_ITEM_STEP_MAX_LENGTH).nullable()
    val quantity = integer("quantity")

    override val primaryKey = PrimaryKey(id)
}

data class OrderItemEntity(
    val id: Int,
    val orderId: Int,
    val productId: Int?,
    val productName: String,
    val unitPrice: BigDecimal,
    val selectedInputIds: List<Int>,
    val stepName: String?,
    val quantity: Int,
)

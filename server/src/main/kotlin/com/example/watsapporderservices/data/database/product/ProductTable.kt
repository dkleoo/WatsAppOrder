package com.example.watsapporderservices.data.database.product

import com.example.watsapporderservices.data.enum.ProductType
import org.jetbrains.exposed.v1.core.Table
import java.math.BigDecimal

const val PRODUCT_NAME_MAX_LENGTH = 160
const val PRODUCT_TYPE_MAX_LENGTH = 20
const val MONEY_PRECISION = 12
const val MONEY_SCALE = 2

object Products : Table("products") {
    val id = integer("id").autoIncrement()
    val storeId = integer("store_id").nullable()
    val name = varchar("name", PRODUCT_NAME_MAX_LENGTH)
    val price = decimal("price", MONEY_PRECISION, MONEY_SCALE)
    val cost = decimal("cost", MONEY_PRECISION, MONEY_SCALE)
    val quantity = integer("quantity")
    val type = enumerationByName("type", PRODUCT_TYPE_MAX_LENGTH, ProductType::class)

    override val primaryKey = PrimaryKey(id)
}

data class ProductEntity(
    val id: Int,
    val storeId: Int?,
    val name: String,
    val price: BigDecimal,
    val cost: BigDecimal,
    val quantity: Int,
    val type: ProductType,
)

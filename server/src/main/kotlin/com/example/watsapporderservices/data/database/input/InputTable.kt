package com.example.watsapporderservices.data.database.input

import com.example.watsapporderservices.data.database.product.MONEY_PRECISION
import com.example.watsapporderservices.data.database.product.MONEY_SCALE
import org.jetbrains.exposed.v1.core.Table
import java.math.BigDecimal

const val INPUT_NAME_MAX_LENGTH = 160

object Inputs : Table("inputs") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", INPUT_NAME_MAX_LENGTH)
    val price = decimal("price", MONEY_PRECISION, MONEY_SCALE)
    val cost = decimal("cost", MONEY_PRECISION, MONEY_SCALE)
    val quantity = integer("quantity")

    override val primaryKey = PrimaryKey(id)
}

data class InputEntity(
    val id: Int,
    val name: String,
    val price: BigDecimal,
    val cost: BigDecimal,
    val quantity: Int,
)

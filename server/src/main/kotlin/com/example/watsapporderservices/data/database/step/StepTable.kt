package com.example.watsapporderservices.data.database.step

import com.example.watsapporderservices.data.database.product.Products
import org.jetbrains.exposed.v1.core.Table

const val STEP_NAME_MAX_LENGTH = 160

object Steps : Table("steps") {
    val id = integer("id").autoIncrement()
    val productId = integer("product_id").references(Products.id)
    val name = varchar("name", STEP_NAME_MAX_LENGTH)
    val position = integer("position")

    override val primaryKey = PrimaryKey(id)
}

data class StepEntity(
    val id: Int,
    val productId: Int,
    val name: String,
    val position: Int,
)

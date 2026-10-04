package com.example.watsapporderservices.data.database.product

import com.example.watsapporderservices.data.enum.ProductType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal

class ProductDao {
    fun findAll(): List<ProductEntity> =
        Products.selectAll().orderBy(Products.id).map { it.toEntity() }

    fun findByStoreId(storeId: Int): List<ProductEntity> =
        Products.selectAll().where { Products.storeId eq storeId }.orderBy(Products.id).map { it.toEntity() }

    fun findById(id: Int): ProductEntity? =
        Products.selectAll().where { Products.id eq id }.singleOrNull()?.toEntity()

    fun insert(
        storeId: Int?,
        name: String,
        price: BigDecimal,
        cost: BigDecimal,
        quantity: Int,
        type: ProductType,
    ): ProductEntity {
        val id = Products.insert {
            it[Products.storeId] = storeId
            it[Products.name] = name
            it[Products.price] = price
            it[Products.cost] = cost
            it[Products.quantity] = quantity
            it[Products.type] = type
        } get Products.id
        return ProductEntity(id, storeId, name, price, cost, quantity, type)
    }

    fun update(
        id: Int,
        name: String,
        price: BigDecimal,
        cost: BigDecimal,
        quantity: Int,
        type: ProductType,
    ): Int = Products.update({ Products.id eq id }) {
        it[Products.name] = name
        it[Products.price] = price
        it[Products.cost] = cost
        it[Products.quantity] = quantity
        it[Products.type] = type
    }

    fun deleteById(id: Int): Int = Products.deleteWhere { Products.id eq id }
}

private fun ResultRow.toEntity(): ProductEntity = ProductEntity(
    id = this[Products.id],
    storeId = this[Products.storeId],
    name = this[Products.name],
    price = this[Products.price],
    cost = this[Products.cost],
    quantity = this[Products.quantity],
    type = this[Products.type],
)

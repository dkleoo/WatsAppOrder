package com.example.watsapporderservices.data.database.input

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal

class InputDao {
    fun findAll(): List<InputEntity> =
        Inputs.selectAll().orderBy(Inputs.id).map { it.toEntity() }

    fun findById(id: Int): InputEntity? =
        Inputs.selectAll().where { Inputs.id eq id }.singleOrNull()?.toEntity()

    fun findByIds(ids: List<Int>): List<InputEntity> {
        if (ids.isEmpty()) return emptyList()
        return Inputs.selectAll().where { Inputs.id inList ids }.map { it.toEntity() }
    }

    fun insert(name: String, price: BigDecimal, cost: BigDecimal, quantity: Int): InputEntity {
        val id = Inputs.insert {
            it[Inputs.name] = name
            it[Inputs.price] = price
            it[Inputs.cost] = cost
            it[Inputs.quantity] = quantity
        } get Inputs.id
        return InputEntity(id, name, price, cost, quantity)
    }
}

private fun ResultRow.toEntity(): InputEntity = InputEntity(
    id = this[Inputs.id],
    name = this[Inputs.name],
    price = this[Inputs.price],
    cost = this[Inputs.cost],
    quantity = this[Inputs.quantity],
)

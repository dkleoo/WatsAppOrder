package com.example.watsapporderservices.data.database.step

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class StepDao {
    fun findByProductId(productId: Int): List<StepEntity> =
        Steps.selectAll()
            .where { Steps.productId eq productId }
            .orderBy(Steps.position to SortOrder.ASC, Steps.id to SortOrder.ASC)
            .map { it.toEntity() }

    fun findByProductIds(productIds: List<Int>): List<StepEntity> {
        if (productIds.isEmpty()) return emptyList()
        return Steps.selectAll()
            .where { Steps.productId inList productIds }
            .orderBy(Steps.position to SortOrder.ASC, Steps.id to SortOrder.ASC)
            .map { it.toEntity() }
    }

    fun findById(id: Int): StepEntity? =
        Steps.selectAll().where { Steps.id eq id }.singleOrNull()?.toEntity()

    fun insert(productId: Int, name: String, position: Int): StepEntity {
        val id = Steps.insert {
            it[Steps.productId] = productId
            it[Steps.name] = name
            it[Steps.position] = position
        } get Steps.id
        return StepEntity(id, productId, name, position)
    }

    fun deleteByProductId(productId: Int): Int = Steps.deleteWhere { Steps.productId eq productId }
}

private fun ResultRow.toEntity(): StepEntity = StepEntity(
    id = this[Steps.id],
    productId = this[Steps.productId],
    name = this[Steps.name],
    position = this[Steps.position],
)

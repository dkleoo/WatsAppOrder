package com.example.watsapporderservices.data.database.store

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class StoreProductDao {
    fun productIdsByStoreId(storeId: Int): List<Int> = transaction {
        StoreProducts.selectAll()
            .where { StoreProducts.storeId eq storeId }
            .map { it[StoreProducts.productId] }
    }

    fun link(storeId: Int, productId: Int) = transaction {
        StoreProducts.insert {
            it[StoreProducts.storeId] = storeId
            it[StoreProducts.productId] = productId
        }
    }

    fun unlink(storeId: Int, productId: Int) = transaction {
        StoreProducts.deleteWhere {
            (StoreProducts.storeId eq storeId) and (StoreProducts.productId eq productId)
        }
    }
}

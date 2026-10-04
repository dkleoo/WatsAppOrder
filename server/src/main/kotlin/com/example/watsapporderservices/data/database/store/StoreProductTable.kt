package com.example.watsapporderservices.data.database.store

import com.example.watsapporderservices.data.database.product.Products
import org.jetbrains.exposed.v1.core.Table

/**
 * Relates products to the store that sells them. It is a separate table so the existing
 * `products` table does not need a schema migration yet.
 */
object StoreProducts : Table("store_products") {
    val storeId = integer("store_id").references(Stores.id)
    val productId = integer("product_id").references(Products.id)

    override val primaryKey = PrimaryKey(storeId, productId)
}

package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.ProductResponse

interface ProductRepository {
    suspend fun getProducts(): List<ProductResponse>

    suspend fun getProduct(id: Int): ProductResponse?

    /** Products of the store identified by its WhatsApp numbers, optionally filtered by [query]. */
    suspend fun searchByStore(
        whatsappBusinessPhone: String?,
        idWhatsApp: String?,
        query: String?,
    ): List<ProductResponse>

    suspend fun saveProduct(request: ProductRequest): ProductResponse?

    suspend fun updateProduct(id: Int, request: ProductRequest): ProductResponse?

    suspend fun deleteProduct(id: Int): Boolean
}

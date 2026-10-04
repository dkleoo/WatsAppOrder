package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.ProductResponse
import com.example.watsapporderservices.domain.usecase.ProductResult

interface ProductRepository {
    suspend fun getProducts(): List<ProductResponse>

    suspend fun getProduct(id: Int): ProductResponse?

    /** Products of the store identified by its WhatsApp numbers, optionally filtered by [query]. */
    suspend fun searchByStore(
        whatsappBusinessPhone: String?,
        idWhatsApp: String?,
        query: String?,
    ): List<ProductResponse>

    /** Creates (linking to `storeId`) or updates a product. */
    suspend fun saveProduct(request: ProductRequest): ProductResult

    suspend fun updateProduct(id: Int, request: ProductRequest): ProductResult

    suspend fun deleteProduct(id: Int): Boolean
}

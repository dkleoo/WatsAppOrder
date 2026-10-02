package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.ProductResponse

interface ProductRepository {
    suspend fun getProducts(): List<ProductResponse>

    suspend fun getProduct(id: Int): ProductResponse?

    suspend fun saveProduct(request: ProductRequest): ProductResponse?

    suspend fun updateProduct(id: Int, request: ProductRequest): ProductResponse?

    suspend fun deleteProduct(id: Int): Boolean
}

package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.ProductResponse
import com.example.watsapporderservices.domain.repository.ProductRepository

class ProductUseCase(private val repository: ProductRepository) {
    suspend fun getProducts(userId: Int): List<ProductResponse> = repository.getProducts(userId)

    suspend fun getProduct(id: Int): ProductResponse? = repository.getProduct(id)

    suspend fun searchByStore(
        whatsappBusinessPhone: String?,
        idWhatsApp: String?,
        query: String?,
    ): List<ProductResponse> = repository.searchByStore(whatsappBusinessPhone, idWhatsApp, query)

    suspend fun saveProduct(userId: Int, request: ProductRequest): ProductResult =
        repository.saveProduct(userId, request)

    suspend fun updateProduct(id: Int, request: ProductRequest): ProductResult =
        repository.updateProduct(id, request)

    suspend fun deleteProduct(id: Int): Boolean = repository.deleteProduct(id)
}

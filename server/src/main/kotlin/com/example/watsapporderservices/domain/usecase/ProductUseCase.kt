package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.ProductResponse
import com.example.watsapporderservices.domain.repository.ProductRepository

class ProductUseCase(private val repository: ProductRepository) {
    suspend fun getProducts(): List<ProductResponse> = repository.getProducts()

    suspend fun getProduct(id: Int): ProductResponse? = repository.getProduct(id)

    suspend fun saveProduct(request: ProductRequest): ProductResponse? = repository.saveProduct(request)

    suspend fun updateProduct(id: Int, request: ProductRequest): ProductResponse? =
        repository.updateProduct(id, request)

    suspend fun deleteProduct(id: Int): Boolean = repository.deleteProduct(id)
}

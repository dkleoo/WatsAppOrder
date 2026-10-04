package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.StoreRequest
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.domain.repository.StoreRepository

class StoreUseCase(private val repository: StoreRepository) {
    suspend fun findByWhatsapp(whatsappBusinessPhone: String?, idWhatsApp: String?): StoreResponse? =
        repository.findByWhatsapp(whatsappBusinessPhone, idWhatsApp)

    suspend fun getStores(): List<StoreResponse> = repository.getStores()

    suspend fun getStore(id: Int): StoreResponse? = repository.getStore(id)

    suspend fun create(request: StoreRequest): StoreResult = repository.create(request)

    suspend fun update(id: Int, request: StoreRequest): StoreResult = repository.update(id, request)
}

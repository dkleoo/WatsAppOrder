package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.StoreRequest
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.domain.usecase.StoreResult

interface StoreRepository {
    suspend fun findByWhatsapp(whatsappBusinessPhone: String?, idWhatsApp: String?): StoreResponse?

    suspend fun getStores(): List<StoreResponse>

    suspend fun getStore(id: Int): StoreResponse?

    suspend fun create(request: StoreRequest): StoreResult

    suspend fun update(id: Int, request: StoreRequest): StoreResult
}

package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.domain.repository.StoreRepository

class StoreUseCase(private val repository: StoreRepository) {
    suspend fun findByWhatsapp(whatsappBusinessPhone: String?, idWhatsApp: String?): StoreResponse? =
        repository.findByWhatsapp(whatsappBusinessPhone, idWhatsApp)
}

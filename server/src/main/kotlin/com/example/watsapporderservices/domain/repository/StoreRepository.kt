package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.StoreResponse

interface StoreRepository {
    suspend fun findByWhatsapp(whatsappBusinessPhone: String?, idWhatsApp: String?): StoreResponse?
}

package com.example.watsapporderservices.data.mapper

import com.example.watsapporderservices.data.database.store.StoreEntity
import kotlinx.serialization.Serializable

@Serializable
data class StoreResponse(
    val id: Int,
    val welcomeMessage: String,
    val address: String,
    val phone: String,
    val whatsappBusinessPhone: String,
    val idWhatsApp: String,
)

fun StoreEntity.toResponse(): StoreResponse = StoreResponse(
    id = id,
    welcomeMessage = welcomeMessage,
    address = address,
    phone = phone,
    whatsappBusinessPhone = whatsappBusinessPhone,
    idWhatsApp = idWhatsApp,
)

package com.example.watsapporderservices.data.mapper

import com.example.watsapporderservices.data.database.store.StoreEntity
import com.example.watsapporderservices.data.enum.StoreErrorCode
import kotlinx.serialization.Serializable

@Serializable
data class StoreRequest(
    val welcomeMessage: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val whatsappBusinessPhone: String? = null,
    val idWhatsApp: String? = null,
    /** Products of this store. On create/update, when present, it replaces the store's product links. */
    val productIds: List<Int>? = null,
)

@Serializable
data class StoreResponse(
    val id: Int,
    val welcomeMessage: String,
    val address: String,
    val phone: String,
    val whatsappBusinessPhone: String,
    val idWhatsApp: String,
    val productIds: List<Int> = emptyList(),
)

fun StoreEntity.toResponse(productIds: List<Int> = emptyList()): StoreResponse = StoreResponse(
    id = id,
    welcomeMessage = welcomeMessage,
    address = address,
    phone = phone,
    whatsappBusinessPhone = whatsappBusinessPhone,
    idWhatsApp = idWhatsApp,
    productIds = productIds,
)

fun StoreErrorCode.toResponse(): ErrorResponse = ErrorResponse(code)

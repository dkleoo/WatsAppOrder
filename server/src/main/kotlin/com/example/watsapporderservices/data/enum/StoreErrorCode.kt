package com.example.watsapporderservices.data.enum

enum class StoreErrorCode(val code: String) {
    INVALID_STORE_ID("invalid_store_id"),
    INVALID_WELCOME_MESSAGE("invalid_welcome_message"),
    INVALID_ADDRESS("invalid_address"),
    INVALID_PHONE("invalid_phone"),
    INVALID_WHATSAPP_PHONE("invalid_whatsapp_phone"),
    INVALID_WHATSAPP_ID("invalid_whatsapp_id"),
    STORE_ALREADY_EXISTS("store_already_exists"),
    STORE_NOT_FOUND("store_not_found"),
}

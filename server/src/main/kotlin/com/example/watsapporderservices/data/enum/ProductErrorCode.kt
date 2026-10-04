package com.example.watsapporderservices.data.enum

enum class ProductErrorCode(val code: String) {
    INVALID_PRODUCT_ID("invalid_product_id"),
    INVALID_STORE_ID("invalid_store_id"),
    PRODUCT_NOT_FOUND("product_not_found"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_NOT_CONFIGURED("store_not_configured"),
}

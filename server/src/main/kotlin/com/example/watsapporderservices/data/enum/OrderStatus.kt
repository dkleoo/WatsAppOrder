package com.example.watsapporderservices.data.enum

enum class OrderStatus {
    DRAFT,
    PENDING,
    IN_KITCHEN,
    ON_THE_WAY,
    DELIVERED,

    /** Rejected by the store (or cancelled by the customer). */
    CANCELLED,
}

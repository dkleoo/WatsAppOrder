package com.example.watsapporderservices.domain.repository

interface NotificationRepository {
    /** Registers a device FCM token for the user. */
    suspend fun registerDevice(userId: Int, token: String)

    /** Sends a new-order push to every device of the store's users. */
    suspend fun notifyNewOrder(
        storeId: Int?,
        orderId: Int,
        sequence: Long,
        total: Double?,
        customerName: String?,
    )
}

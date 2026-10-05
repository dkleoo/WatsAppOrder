package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.notification.DeviceTokenDao
import com.example.watsapporderservices.data.security.FirebaseMessagingClient
import com.example.watsapporderservices.domain.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepositoryImpl(
    private val deviceTokenDao: DeviceTokenDao,
    private val messagingClient: FirebaseMessagingClient,
) : NotificationRepository {
    override suspend fun registerDevice(userId: Int, token: String) {
        withContext(Dispatchers.IO) { deviceTokenDao.upsert(userId, token) }
    }

    override suspend fun notifyNewOrder(
        storeId: Int?,
        orderId: Int,
        sequence: Long,
        total: Double?,
        customerName: String?,
    ) {
        if (storeId == null || !messagingClient.isConfigured) return

        val tokens = withContext(Dispatchers.IO) {
            deviceTokenDao.findUserIdsByStore(storeId)
                .flatMap { deviceTokenDao.findByUserId(it) }
                .map { it.token }
                .distinct()
        }
        if (tokens.isEmpty()) return

        val title = "Nuevo pedido #$orderId"
        val body = buildString {
            append(customerName?.takeIf { it.isNotBlank() } ?: "Cliente")
            if (total != null) append(" - total $ $total")
        }
        val data = mapOf(
            "type" to "order",
            "orderId" to orderId.toString(),
            "sequence" to sequence.toString(),
        )
        withContext(Dispatchers.IO) {
            tokens.forEach { token ->
                val ok = messagingClient.sendToToken(token, title, body, data)
                if (!ok) {
                    // Token may be stale/expired: drop it so we stop trying.
                    runCatching { deviceTokenDao.delete(token) }
                }
            }
        }
    }
}

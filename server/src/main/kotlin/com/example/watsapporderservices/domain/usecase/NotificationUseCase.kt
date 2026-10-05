package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.domain.repository.NotificationRepository

class NotificationUseCase(
    private val repository: NotificationRepository,
) {
    suspend fun registerDevice(userId: Int, token: String) = repository.registerDevice(userId, token)
}

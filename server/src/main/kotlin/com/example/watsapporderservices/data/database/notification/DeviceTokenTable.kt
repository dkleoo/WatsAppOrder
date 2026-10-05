package com.example.watsapporderservices.data.database.notification

import com.example.watsapporderservices.data.database.user.Users
import org.jetbrains.exposed.v1.core.Table

const val DEVICE_TOKEN_MAX_LENGTH = 512

object DeviceTokens : Table("device_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val token = varchar("token", DEVICE_TOKEN_MAX_LENGTH).uniqueIndex()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

data class DeviceTokenEntity(
    val id: Int,
    val userId: Int,
    val token: String,
    val createdAt: Long,
)

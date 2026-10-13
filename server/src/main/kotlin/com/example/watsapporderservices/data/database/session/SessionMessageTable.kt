package com.example.watsapporderservices.data.database.session

import org.jetbrains.exposed.v1.core.Table

const val SESSION_MESSAGE_ROLE_MAX_LENGTH = 20

object SessionMessages : Table("session_messages") {
    val id = integer("id").autoIncrement()
    val sessionId = integer("session_id").references(Sessions.id)
    val role = varchar("role", SESSION_MESSAGE_ROLE_MAX_LENGTH)
    val content = text("content")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

data class SessionMessageEntity(
    val id: Int,
    val sessionId: Int,
    val role: String,
    val content: String,
    val createdAt: Long,
)

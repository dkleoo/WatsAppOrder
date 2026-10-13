package com.example.watsapporderservices.data.database.session

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SessionMessageDao {
    fun insert(sessionId: Int, role: String, content: String, createdAt: Long): SessionMessageEntity = transaction {
        val id = SessionMessages.insert {
            it[SessionMessages.sessionId] = sessionId
            it[SessionMessages.role] = role
            it[SessionMessages.content] = content
            it[SessionMessages.createdAt] = createdAt
        } get SessionMessages.id
        SessionMessageEntity(id, sessionId, role, content, createdAt)
    }

    /** Last [limit] messages of the session, oldest first. */
    fun findBySessionId(sessionId: Int, limit: Int): List<SessionMessageEntity> = transaction {
        SessionMessages.selectAll()
            .where { SessionMessages.sessionId eq sessionId }
            .orderBy(SessionMessages.id to SortOrder.DESC)
            .limit(limit)
            .map { it.toEntity() }
            .reversed()
    }

    fun deleteBySessionId(sessionId: Int): Int = transaction {
        SessionMessages.deleteWhere { SessionMessages.sessionId eq sessionId }
    }
}

private fun ResultRow.toEntity(): SessionMessageEntity = SessionMessageEntity(
    id = this[SessionMessages.id],
    sessionId = this[SessionMessages.sessionId],
    role = this[SessionMessages.role],
    content = this[SessionMessages.content],
    createdAt = this[SessionMessages.createdAt],
)

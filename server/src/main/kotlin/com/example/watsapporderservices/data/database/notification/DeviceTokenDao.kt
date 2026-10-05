package com.example.watsapporderservices.data.database.notification

import com.example.watsapporderservices.data.database.user.Users
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class DeviceTokenDao {
    fun findByUserId(userId: Int): List<DeviceTokenEntity> = transaction {
        DeviceTokens.selectAll().where { DeviceTokens.userId eq userId }.map { it.toEntity() }
    }

    /** Ids of the users that own a store (their devices receive the store's order notifications). */
    fun findUserIdsByStore(storeId: Int): List<Int> = transaction {
        Users.selectAll().where { Users.storeId eq storeId }.map { it[Users.id] }
    }

    /** Registers (or re-points) a token for a user. Tokens are unique, so upsert on the token. */
    fun upsert(userId: Int, token: String): DeviceTokenEntity = transaction {
        val existing = DeviceTokens.selectAll().where { DeviceTokens.token eq token }.singleOrNull()
        val now = System.currentTimeMillis()
        if (existing == null) {
            val id = DeviceTokens.insert {
                it[DeviceTokens.userId] = userId
                it[DeviceTokens.token] = token
                it[DeviceTokens.createdAt] = now
            } get DeviceTokens.id
            DeviceTokenEntity(id, userId, token, now)
        } else {
            DeviceTokens.update({ DeviceTokens.token eq token }) { it[DeviceTokens.userId] = userId }
            DeviceTokenEntity(existing[DeviceTokens.id], userId, token, existing[DeviceTokens.createdAt])
        }
    }

    fun delete(token: String) = transaction {
        DeviceTokens.deleteWhere { DeviceTokens.token eq token }
    }
}

private fun ResultRow.toEntity(): DeviceTokenEntity = DeviceTokenEntity(
    id = this[DeviceTokens.id],
    userId = this[DeviceTokens.userId],
    token = this[DeviceTokens.token],
    createdAt = this[DeviceTokens.createdAt],
)

package com.example.watsapporderservices.data.database.user

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class UserDao {
    fun findByEmail(email: String): UserEntity? = transaction {
        Users.selectAll().where { Users.email eq email }.singleOrNull()?.toEntity()
    }

    fun findById(id: Int): UserEntity? = transaction {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toEntity()
    }

    fun findByFirebaseUid(firebaseUid: String): UserEntity? = transaction {
        Users.selectAll().where { Users.firebaseUid eq firebaseUid }.singleOrNull()?.toEntity()
    }

    fun existsByEmail(email: String): Boolean = transaction {
        Users.selectAll().where { Users.email eq email }.count() > 0L
    }

    fun create(email: String, name: String, passwordHash: String): UserEntity = transaction {
        val createdAt = System.currentTimeMillis()
        val id = Users.insert {
            it[Users.email] = email
            it[Users.name] = name
            it[Users.passwordHash] = passwordHash
            it[Users.createdAt] = createdAt
        } get Users.id
        UserEntity(id, email, name, passwordHash, null, null, createdAt)
    }

    fun createFederated(
        email: String,
        name: String,
        passwordHash: String,
        firebaseUid: String,
        authProvider: String,
    ): UserEntity = transaction {
        val createdAt = System.currentTimeMillis()
        val id = Users.insert {
            it[Users.email] = email
            it[Users.name] = name
            it[Users.passwordHash] = passwordHash
            it[Users.firebaseUid] = firebaseUid
            it[Users.authProvider] = authProvider
            it[Users.createdAt] = createdAt
        } get Users.id
        UserEntity(id, email, name, passwordHash, firebaseUid, authProvider, createdAt)
    }

    fun linkFederation(
        id: Int,
        firebaseUid: String,
        authProvider: String,
        email: String?,
        name: String?,
    ): UserEntity = transaction {
        Users.update({ Users.id eq id }) {
            it[Users.firebaseUid] = firebaseUid
            it[Users.authProvider] = authProvider
            if (email != null) it[Users.email] = email
            if (name != null) it[Users.name] = name
        }
        Users.selectAll().where { Users.id eq id }.single().toEntity()
    }
}

private fun ResultRow.toEntity(): UserEntity = UserEntity(
    id = this[Users.id],
    email = this[Users.email],
    name = this[Users.name],
    passwordHash = this[Users.passwordHash],
    firebaseUid = this[Users.firebaseUid],
    authProvider = this[Users.authProvider],
    createdAt = this[Users.createdAt],
)

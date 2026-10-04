package com.example.watsapporderservices.data.database.store

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class StoreDao {
    fun findById(id: Int): StoreEntity? = transaction {
        Stores.selectAll().where { Stores.id eq id }.singleOrNull()?.toEntity()
    }

    fun findByUserId(userId: Int): StoreEntity? = transaction {
        Stores.selectAll().where { Stores.userId eq userId }.singleOrNull()?.toEntity()
    }

    fun findByWhatsappBusinessPhone(phone: String): StoreEntity? = transaction {
        Stores.selectAll().where { Stores.whatsappBusinessPhone eq phone }.singleOrNull()?.toEntity()
    }

    fun findByIdWhatsApp(idWhatsApp: String): StoreEntity? = transaction {
        Stores.selectAll().where { Stores.idWhatsApp eq idWhatsApp }.singleOrNull()?.toEntity()
    }

    fun findAll(): List<StoreEntity> = transaction {
        Stores.selectAll().orderBy(Stores.id).map { it.toEntity() }
    }

    fun insert(
        userId: Int?,
        welcomeMessage: String,
        address: String,
        phone: String,
        whatsappBusinessPhone: String,
        idWhatsApp: String,
    ): StoreEntity = transaction {
        val id = Stores.insert {
            it[Stores.userId] = userId
            it[Stores.welcomeMessage] = welcomeMessage
            it[Stores.address] = address
            it[Stores.phone] = phone
            it[Stores.whatsappBusinessPhone] = whatsappBusinessPhone
            it[Stores.idWhatsApp] = idWhatsApp
        } get Stores.id
        StoreEntity(id, userId, welcomeMessage, address, phone, whatsappBusinessPhone, idWhatsApp)
    }

    fun updateFull(store: StoreEntity): Int = transaction {
        Stores.update({ Stores.id eq store.id }) {
            it[Stores.userId] = store.userId
            it[Stores.welcomeMessage] = store.welcomeMessage
            it[Stores.address] = store.address
            it[Stores.phone] = store.phone
            it[Stores.whatsappBusinessPhone] = store.whatsappBusinessPhone
            it[Stores.idWhatsApp] = store.idWhatsApp
        }
    }
}

private fun ResultRow.toEntity(): StoreEntity = StoreEntity(
    id = this[Stores.id],
    userId = this[Stores.userId],
    welcomeMessage = this[Stores.welcomeMessage],
    address = this[Stores.address],
    phone = this[Stores.phone],
    whatsappBusinessPhone = this[Stores.whatsappBusinessPhone],
    idWhatsApp = this[Stores.idWhatsApp],
)

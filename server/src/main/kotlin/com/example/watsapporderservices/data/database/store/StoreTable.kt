package com.example.watsapporderservices.data.database.store

import org.jetbrains.exposed.v1.core.Table

const val WELCOME_MESSAGE_MAX_LENGTH = 1000
const val STORE_ADDRESS_MAX_LENGTH = 300
const val STORE_PHONE_MAX_LENGTH = 40
const val STORE_WHATSAPP_PHONE_MAX_LENGTH = 40
const val STORE_WHATSAPP_ID_MAX_LENGTH = 64

object Stores : Table("stores") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").nullable()
    val welcomeMessage = varchar("welcome_message", WELCOME_MESSAGE_MAX_LENGTH)
    val address = varchar("address", STORE_ADDRESS_MAX_LENGTH)
    val phone = varchar("phone", STORE_PHONE_MAX_LENGTH)
    val whatsappBusinessPhone = varchar("whatsapp_business_phone", STORE_WHATSAPP_PHONE_MAX_LENGTH)
    val idWhatsApp = varchar("id_whatsapp", STORE_WHATSAPP_ID_MAX_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

data class StoreEntity(
    val id: Int,
    val userId: Int?,
    val welcomeMessage: String,
    val address: String,
    val phone: String,
    val whatsappBusinessPhone: String,
    val idWhatsApp: String,
)

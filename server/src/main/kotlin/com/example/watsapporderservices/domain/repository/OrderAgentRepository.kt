package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.StoreResponse

interface OrderAgentRepository {
    /**
     * Handles one inbound WhatsApp message: resumes the session, records the message, runs the
     * tool-calling agent against the database and sends the reply (plus a greeting when the session is new).
     */
    suspend fun handleMessage(customerPhone: String, store: StoreResponse?, text: String)
}

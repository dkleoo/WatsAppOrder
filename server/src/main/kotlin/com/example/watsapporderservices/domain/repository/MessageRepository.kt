package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.SendMessageRequest
import com.example.watsapporderservices.domain.usecase.MessageResult

interface MessageRepository {
    suspend fun send(request: SendMessageRequest): MessageResult

    /** Sends a text message to a full international number (digits only), without splitting it. */
    suspend fun sendText(to: String, message: String): MessageResult

    /** Sends an interactive list (max 10 rows). Each row id is used later to resolve the selection. */
    suspend fun sendList(
        to: String,
        body: String,
        buttonText: String,
        rows: List<MessageOption>,
    ): MessageResult

    /**
     * Sends an interactive list with a "product" section and a "more" section (for pagination),
     * so the "Ver más" row is visually separated from the products.
     */
    suspend fun sendListSections(
        to: String,
        body: String,
        buttonText: String,
        productRows: List<MessageOption>,
        moreRows: List<MessageOption>,
    ): MessageResult

    /** Sends an interactive reply-buttons message (max 3 buttons). */
    suspend fun sendButtons(
        to: String,
        body: String,
        buttons: List<MessageOption>,
    ): MessageResult
}

/** One interactive option: [id] is what the webhook receives back, [title] is what the client sees. */
data class MessageOption(
    val id: String,
    val title: String,
    val description: String? = null,
)

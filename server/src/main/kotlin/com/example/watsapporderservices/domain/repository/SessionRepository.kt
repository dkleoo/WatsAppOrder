package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.enum.SessionState
import com.example.watsapporderservices.domain.usecase.SessionInfo
import com.example.watsapporderservices.domain.usecase.SessionMessage

interface SessionRepository {
    /** Resumes the customer's session, or starts a new one if it expired (30 minutes without messages). */
    suspend fun resume(customerPhone: String, storeId: Int?): SessionInfo

    suspend fun saveState(
        sessionId: Int,
        storeId: Int?,
        state: SessionState,
        optionProductIds: List<Int>,
        orderId: Int?,
        stepIndex: Int = 0,
    )

    suspend fun appendMessage(sessionId: Int, role: String, content: String)

    /** Last [limit] messages of the session, oldest first. */
    suspend fun history(sessionId: Int, limit: Int): List<SessionMessage>

    suspend fun clearHistory(sessionId: Int)

    /** Points the session at its current draft order (null to unlink). */
    suspend fun linkOrder(sessionId: Int, storeId: Int?, orderId: Int?)
}

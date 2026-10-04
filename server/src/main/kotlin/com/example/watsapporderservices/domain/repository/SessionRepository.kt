package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.enum.SessionState
import com.example.watsapporderservices.domain.usecase.SessionInfo

interface SessionRepository {
    /** Resumes the customer's session, or starts a new one if it expired (2 minutes without messages). */
    suspend fun resume(customerPhone: String, storeId: Int?): SessionInfo

    suspend fun saveState(sessionId: Int, storeId: Int?, state: SessionState, optionProductIds: List<Int>)
}

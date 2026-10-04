package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.session.SessionDao
import com.example.watsapporderservices.data.database.session.SessionEntity
import com.example.watsapporderservices.data.enum.SessionState
import com.example.watsapporderservices.domain.repository.SessionRepository
import com.example.watsapporderservices.domain.usecase.SessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SESSION_TTL_MILLIS = 2 * 60 * 1000L

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
) : SessionRepository {
    override suspend fun resume(customerPhone: String, storeId: Int?): SessionInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = sessionDao.findByCustomerPhone(customerPhone)
        when {
            existing == null -> {
                val created = sessionDao.insert(customerPhone, storeId, SessionState.IDLE, emptyList(), now)
                created.toInfo(isNew = true)
            }

            now - existing.lastActivityAt > SESSION_TTL_MILLIS -> {
                sessionDao.update(existing.id, storeId, SessionState.IDLE, emptyList(), now)
                existing.copy(
                    storeId = storeId,
                    state = SessionState.IDLE,
                    optionProductIds = emptyList(),
                    lastActivityAt = now,
                ).toInfo(isNew = true)
            }

            else -> {
                sessionDao.update(existing.id, storeId, existing.state, existing.optionProductIds, now)
                existing.copy(storeId = storeId, lastActivityAt = now).toInfo(isNew = false)
            }
        }
    }

    override suspend fun saveState(
        sessionId: Int,
        storeId: Int?,
        state: SessionState,
        optionProductIds: List<Int>,
    ) {
        withContext(Dispatchers.IO) {
            sessionDao.update(sessionId, storeId, state, optionProductIds, System.currentTimeMillis())
        }
    }
}

private fun SessionEntity.toInfo(isNew: Boolean): SessionInfo = SessionInfo(
    id = id,
    customerPhone = customerPhone,
    storeId = storeId,
    state = state,
    optionProductIds = optionProductIds,
    lastActivityAt = lastActivityAt,
    isNew = isNew,
)

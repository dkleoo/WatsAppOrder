package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.store.StoreDao
import com.example.watsapporderservices.data.database.store.StoreEntity
import com.example.watsapporderservices.data.database.user.UserDao
import com.example.watsapporderservices.data.enum.StoreErrorCode
import com.example.watsapporderservices.data.mapper.StoreRequest
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.repository.StoreRepository
import com.example.watsapporderservices.domain.usecase.StoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private const val DEFAULT_WELCOME_MESSAGE = "¡Hola! Bienvenido a mi tienda 🛒 ¿Qué deseas ordenar?"

class StoreRepositoryImpl(
    private val storeDao: StoreDao,
    private val userDao: UserDao,
) : StoreRepository {
    override suspend fun findByWhatsapp(
        whatsappBusinessPhone: String?,
        idWhatsApp: String?,
    ): StoreResponse? = withContext(Dispatchers.IO) {
        val phoneDigits = whatsappBusinessPhone?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
        val store = phoneDigits?.let { storeDao.findByWhatsappBusinessPhone(it) }
            ?: idWhatsApp?.takeIf { it.isNotBlank() }?.let { storeDao.findByIdWhatsApp(it) }
        store?.toResponse()
    }

    override suspend fun getStores(): List<StoreResponse> = withContext(Dispatchers.IO) {
        storeDao.findAll().map { it.toResponse() }
    }

    override suspend fun getStore(id: Int): StoreResponse? = withContext(Dispatchers.IO) {
        storeDao.findById(id)?.toResponse()
    }

    override suspend fun getStoreByUser(userId: Int): StoreResponse? = withContext(Dispatchers.IO) {
        storeDao.findByUserId(userId)?.toResponse()
    }

    override suspend fun create(userId: Int, request: StoreRequest): StoreResult = withContext(Dispatchers.IO) {
        transaction {
            val existing = storeDao.findByUserId(userId)
            if (existing != null) return@transaction updateStore(existing, request)
            createStore(userId, request)
        }
    }

    override suspend fun update(id: Int, request: StoreRequest): StoreResult = withContext(Dispatchers.IO) {
        transaction {
            val store = storeDao.findById(id) ?: return@transaction StoreResult.NotFound
            updateStore(store, request)
        }
    }

    private fun createStore(userId: Int, request: StoreRequest): StoreResult {
        val parsed = parse(request)
        if (parsed is Parsed.Invalid) return StoreResult.Invalid(parsed.code)
        val store = (parsed as Parsed.Ok).store

        val conflict = hasConflict(store.whatsappBusinessPhone, store.idWhatsApp, excludeId = null)
        if (conflict) return StoreResult.AlreadyExists

        val created = storeDao.insert(
            userId = userId,
            welcomeMessage = store.welcomeMessage,
            address = store.address,
            phone = store.phone,
            whatsappBusinessPhone = store.whatsappBusinessPhone,
            idWhatsApp = store.idWhatsApp,
        )
        userDao.updateStoreId(userId, created.id)
        return StoreResult.Success(created.toResponse())
    }

    private fun updateStore(store: StoreEntity, request: StoreRequest): StoreResult {
        // PARTIAL: only the fields present in the body are replaced; the rest keep their current value.
        val updated = store.copy(
            welcomeMessage = request.welcomeMessage?.trim() ?: store.welcomeMessage,
            address = request.address?.trim() ?: store.address,
            phone = request.phone?.trim() ?: store.phone,
            whatsappBusinessPhone = request.whatsappBusinessPhone?.filter { it.isDigit() }
                ?.takeIf { it.isNotBlank() } ?: store.whatsappBusinessPhone,
            idWhatsApp = request.idWhatsApp?.trim()?.takeIf { it.isNotBlank() } ?: store.idWhatsApp,
        )
        if (updated.welcomeMessage.isBlank() || updated.address.isBlank() ||
            updated.phone.isBlank() || updated.whatsappBusinessPhone.isBlank() || updated.idWhatsApp.isBlank()
        ) {
            return StoreResult.Invalid(StoreErrorCode.INVALID_ADDRESS)
        }
        if (hasConflict(updated.whatsappBusinessPhone, updated.idWhatsApp, excludeId = store.id)) {
            return StoreResult.AlreadyExists
        }
        storeDao.updateFull(updated)
        return StoreResult.Success(updated.toResponse())
    }

    private fun hasConflict(whatsappBusinessPhone: String, idWhatsApp: String, excludeId: Int?): Boolean {
        val byPhone = storeDao.findByWhatsappBusinessPhone(whatsappBusinessPhone)
        val byIdWhatsApp = storeDao.findByIdWhatsApp(idWhatsApp)
        return (byPhone != null && byPhone.id != excludeId) || (byIdWhatsApp != null && byIdWhatsApp.id != excludeId)
    }

    private fun parse(request: StoreRequest): Parsed {
        val store = StoreEntity(
            id = 0,
            userId = null,
            welcomeMessage = request.welcomeMessage?.trim().orEmpty(),
            address = request.address?.trim().orEmpty(),
            phone = request.phone?.trim().orEmpty(),
            whatsappBusinessPhone = request.whatsappBusinessPhone?.filter { it.isDigit() }.orEmpty(),
            idWhatsApp = request.idWhatsApp?.trim().orEmpty(),
        )
        return when {
            store.welcomeMessage.isBlank() -> Parsed.Invalid(StoreErrorCode.INVALID_WELCOME_MESSAGE)
            store.address.isBlank() -> Parsed.Invalid(StoreErrorCode.INVALID_ADDRESS)
            store.phone.isBlank() -> Parsed.Invalid(StoreErrorCode.INVALID_PHONE)
            store.whatsappBusinessPhone.isBlank() -> Parsed.Invalid(StoreErrorCode.INVALID_WHATSAPP_PHONE)
            store.idWhatsApp.isBlank() -> Parsed.Invalid(StoreErrorCode.INVALID_WHATSAPP_ID)
            else -> Parsed.Ok(store)
        }
    }

    private sealed interface Parsed {
        data class Ok(val store: StoreEntity) : Parsed

        data class Invalid(val code: StoreErrorCode) : Parsed
    }
}

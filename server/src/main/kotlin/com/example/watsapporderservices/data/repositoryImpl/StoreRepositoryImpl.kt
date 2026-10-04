package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.store.StoreDao
import com.example.watsapporderservices.data.database.store.StoreEntity
import com.example.watsapporderservices.data.enum.StoreErrorCode
import com.example.watsapporderservices.data.mapper.StoreRequest
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.repository.StoreRepository
import com.example.watsapporderservices.domain.usecase.StoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StoreRepositoryImpl(
    private val storeDao: StoreDao,
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

    override suspend fun create(request: StoreRequest): StoreResult = withContext(Dispatchers.IO) {
        val parsed = parse(request)
        if (parsed is Parsed.Invalid) return@withContext StoreResult.Invalid(parsed.code)
        val store = (parsed as Parsed.Ok).store

        val alreadyExists = storeDao.findByWhatsappBusinessPhone(store.whatsappBusinessPhone) != null ||
            storeDao.findByIdWhatsApp(store.idWhatsApp) != null
        if (alreadyExists) return@withContext StoreResult.AlreadyExists

        val created = storeDao.insert(
            welcomeMessage = store.welcomeMessage,
            address = store.address,
            phone = store.phone,
            whatsappBusinessPhone = store.whatsappBusinessPhone,
            idWhatsApp = store.idWhatsApp,
        )
        StoreResult.Success(created.toResponse())
    }

    override suspend fun update(id: Int, request: StoreRequest): StoreResult = withContext(Dispatchers.IO) {
        val parsed = parse(request)
        if (parsed is Parsed.Invalid) return@withContext StoreResult.Invalid(parsed.code)
        val store = (parsed as Parsed.Ok).store

        if (storeDao.findById(id) == null) return@withContext StoreResult.NotFound

        val byPhone = storeDao.findByWhatsappBusinessPhone(store.whatsappBusinessPhone)
        val byIdWhatsApp = storeDao.findByIdWhatsApp(store.idWhatsApp)
        val takenByOther = (byPhone != null && byPhone.id != id) || (byIdWhatsApp != null && byIdWhatsApp.id != id)
        if (takenByOther) return@withContext StoreResult.AlreadyExists

        storeDao.update(
            id = id,
            welcomeMessage = store.welcomeMessage,
            address = store.address,
            phone = store.phone,
            whatsappBusinessPhone = store.whatsappBusinessPhone,
            idWhatsApp = store.idWhatsApp,
        )
        StoreResult.Success(
            StoreResponse(
                id = id,
                welcomeMessage = store.welcomeMessage,
                address = store.address,
                phone = store.phone,
                whatsappBusinessPhone = store.whatsappBusinessPhone,
                idWhatsApp = store.idWhatsApp,
            ),
        )
    }

    private fun parse(request: StoreRequest): Parsed {
        val store = StoreEntity(
            id = 0,
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

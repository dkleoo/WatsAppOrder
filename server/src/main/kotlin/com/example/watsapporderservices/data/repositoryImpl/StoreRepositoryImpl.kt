package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.store.StoreDao
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.repository.StoreRepository
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
}

package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.input.InputDao
import com.example.watsapporderservices.data.mapper.InputCreateRequest
import com.example.watsapporderservices.data.mapper.InputResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.repository.InputRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal

class InputRepositoryImpl(
    private val inputDao: InputDao,
) : InputRepository {
    override suspend fun getInputs(): List<InputResponse> = withContext(Dispatchers.IO) {
        transaction { inputDao.findAll().map { it.toResponse() } }
    }

    override suspend fun getInput(id: Int): InputResponse? = withContext(Dispatchers.IO) {
        transaction { inputDao.findById(id)?.toResponse() }
    }

    override suspend fun createInput(request: InputCreateRequest): InputResponse = withContext(Dispatchers.IO) {
        transaction {
            inputDao.insert(
                name = request.name,
                price = BigDecimal.valueOf(request.price),
                cost = BigDecimal.valueOf(request.cost),
                quantity = request.quantity,
            ).toResponse()
        }
    }
}

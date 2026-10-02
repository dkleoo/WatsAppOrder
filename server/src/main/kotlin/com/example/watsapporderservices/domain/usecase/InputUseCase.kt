package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.mapper.InputCreateRequest
import com.example.watsapporderservices.data.mapper.InputResponse
import com.example.watsapporderservices.domain.repository.InputRepository

class InputUseCase(private val repository: InputRepository) {
    suspend fun getInputs(): List<InputResponse> = repository.getInputs()

    suspend fun getInput(id: Int): InputResponse? = repository.getInput(id)

    suspend fun createInput(request: InputCreateRequest): InputResponse = repository.createInput(request)
}

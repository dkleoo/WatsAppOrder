package com.example.watsapporderservices.domain.repository

import com.example.watsapporderservices.data.mapper.InputCreateRequest
import com.example.watsapporderservices.data.mapper.InputResponse

interface InputRepository {
    suspend fun getInputs(): List<InputResponse>

    suspend fun getInput(id: Int): InputResponse?

    suspend fun createInput(request: InputCreateRequest): InputResponse
}

package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.InputErrorCode
import com.example.watsapporderservices.data.mapper.InputCreateRequest
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.usecase.InputUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.inputRoutes(useCase: InputUseCase) {
    route("/inputs") {
        get {
            call.respond(useCase.getInputs())
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, InputErrorCode.INVALID_INPUT_ID.toResponse())
                return@get
            }
            val input = useCase.getInput(id)
            if (input == null) {
                call.respond(HttpStatusCode.NotFound, InputErrorCode.INPUT_NOT_FOUND.toResponse())
                return@get
            }
            call.respond(input)
        }

        post {
            val request = call.receive<InputCreateRequest>()
            call.respond(HttpStatusCode.Created, useCase.createInput(request))
        }
    }
}

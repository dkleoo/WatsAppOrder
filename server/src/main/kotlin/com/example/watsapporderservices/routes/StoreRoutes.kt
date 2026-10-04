package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.StoreErrorCode
import com.example.watsapporderservices.data.mapper.StoreRequest
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.usecase.StoreResult
import com.example.watsapporderservices.domain.usecase.StoreUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.storeRoutes(useCase: StoreUseCase) {
    route("/stores") {
        get {
            call.respond(useCase.getStores())
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, StoreErrorCode.INVALID_STORE_ID.toResponse())
                return@get
            }
            val store = useCase.getStore(id)
            if (store == null) {
                call.respond(HttpStatusCode.NotFound, StoreErrorCode.STORE_NOT_FOUND.toResponse())
                return@get
            }
            call.respond(store)
        }

        post {
            respondStoreResult(call, useCase.create(call.receive<StoreRequest>()), HttpStatusCode.Created)
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, StoreErrorCode.INVALID_STORE_ID.toResponse())
                return@put
            }
            respondStoreResult(call, useCase.update(id, call.receive<StoreRequest>()))
        }
    }
}

private suspend fun respondStoreResult(
    call: ApplicationCall,
    result: StoreResult,
    successStatus: HttpStatusCode = HttpStatusCode.OK,
) {
    when (result) {
        is StoreResult.Success -> call.respond(successStatus, result.store)

        is StoreResult.Invalid -> call.respond(HttpStatusCode.BadRequest, result.code.toResponse())

        StoreResult.AlreadyExists ->
            call.respond(HttpStatusCode.Conflict, StoreErrorCode.STORE_ALREADY_EXISTS.toResponse())

        StoreResult.NotFound ->
            call.respond(HttpStatusCode.NotFound, StoreErrorCode.STORE_NOT_FOUND.toResponse())
    }
}

package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.ProductErrorCode
import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.usecase.ProductUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.productRoutes(useCase: ProductUseCase) {
    route("/products") {
        get {
            call.respond(useCase.getProducts())
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ProductErrorCode.INVALID_PRODUCT_ID.toResponse())
                return@get
            }
            val product = useCase.getProduct(id)
            if (product == null) {
                call.respond(HttpStatusCode.NotFound, ProductErrorCode.PRODUCT_NOT_FOUND.toResponse())
                return@get
            }
            call.respond(product)
        }

        post {
            val request = call.receive<ProductRequest>()
            val saved = useCase.saveProduct(request)
            if (saved == null) {
                call.respond(HttpStatusCode.NotFound, ProductErrorCode.PRODUCT_NOT_FOUND.toResponse())
                return@post
            }
            val status = if (request.id == null) HttpStatusCode.Created else HttpStatusCode.OK
            call.respond(status, saved)
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ProductErrorCode.INVALID_PRODUCT_ID.toResponse())
                return@put
            }
            val request = call.receive<ProductRequest>()
            val updated = useCase.updateProduct(id, request)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, ProductErrorCode.PRODUCT_NOT_FOUND.toResponse())
                return@put
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ProductErrorCode.INVALID_PRODUCT_ID.toResponse())
                return@delete
            }
            if (!useCase.deleteProduct(id)) {
                call.respond(HttpStatusCode.NotFound, ProductErrorCode.PRODUCT_NOT_FOUND.toResponse())
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

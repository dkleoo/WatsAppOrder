package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.enum.ProductErrorCode
import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.data.security.JWT_AUTH_NAME
import com.example.watsapporderservices.domain.usecase.ProductResult
import com.example.watsapporderservices.domain.usecase.ProductUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
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
        authenticate(JWT_AUTH_NAME) {
            get {
                val userId = call.userId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
                    return@get
                }
                call.respond(useCase.getProducts(userId))
            }
        }

        get("/catalog") {
            val whatsapp = call.request.queryParameters["whatsappBusinessPhone"]
                ?: call.request.queryParameters["whatsapp"]
            val idWhatsApp = call.request.queryParameters["idWhatsApp"]
            call.respond(useCase.searchByStore(whatsapp, idWhatsApp, null))
        }

        get("/filter") {
            val whatsapp = call.request.queryParameters["whatsappBusinessPhone"]
                ?: call.request.queryParameters["whatsapp"]
            val idWhatsApp = call.request.queryParameters["idWhatsApp"]
            val query = call.request.queryParameters["q"]
            call.respond(useCase.searchByStore(whatsapp, idWhatsApp, query))
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

        authenticate(JWT_AUTH_NAME) {
            post {
                val userId = call.userId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
                    return@post
                }
                val request = call.receive<ProductRequest>()
                when (val result = useCase.saveProduct(userId, request)) {
                    is ProductResult.Success -> call.respond(
                        if (request.id == null) HttpStatusCode.Created else HttpStatusCode.OK,
                        result.product,
                    )

                    is ProductResult.Invalid ->
                        call.respond(HttpStatusCode.BadRequest, result.code.toResponse())

                    ProductResult.ProductNotFound ->
                        call.respond(HttpStatusCode.NotFound, ProductErrorCode.PRODUCT_NOT_FOUND.toResponse())

                    ProductResult.StoreNotFound ->
                        call.respond(HttpStatusCode.BadRequest, ProductErrorCode.STORE_NOT_FOUND.toResponse())
                }
            }
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ProductErrorCode.INVALID_PRODUCT_ID.toResponse())
                return@put
            }
            val request = call.receive<ProductRequest>()
            when (val result = useCase.updateProduct(id, request)) {
                is ProductResult.Success -> call.respond(HttpStatusCode.OK, result.product)

                is ProductResult.Invalid ->
                    call.respond(HttpStatusCode.BadRequest, result.code.toResponse())

                ProductResult.ProductNotFound ->
                    call.respond(HttpStatusCode.NotFound, ProductErrorCode.PRODUCT_NOT_FOUND.toResponse())

                ProductResult.StoreNotFound ->
                    call.respond(HttpStatusCode.BadRequest, ProductErrorCode.STORE_NOT_FOUND.toResponse())
            }
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

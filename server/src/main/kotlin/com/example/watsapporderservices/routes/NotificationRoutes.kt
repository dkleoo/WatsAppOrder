package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.mapper.RegisterDeviceRequest
import com.example.watsapporderservices.data.mapper.RegisterDeviceResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.data.security.JWT_AUTH_NAME
import com.example.watsapporderservices.domain.usecase.NotificationUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.notificationRoutes(useCase: NotificationUseCase) {
    route("/notifications") {
        authenticate(JWT_AUTH_NAME) {
            // The app registers its FCM device token here after login.
            post("/device") {
                val userId = call.userId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
                    return@post
                }
                val token = call.receive<RegisterDeviceRequest>().token?.trim().orEmpty()
                if (token.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, AuthErrorCode.INVALID_REQUEST.toResponse())
                    return@post
                }
                useCase.registerDevice(userId, token)
                call.respond(HttpStatusCode.OK, RegisterDeviceResponse(registered = true))
            }
        }
    }
}

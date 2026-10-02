package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.mapper.AuthResponse
import com.example.watsapporderservices.data.mapper.FederatedAuthRequest
import com.example.watsapporderservices.data.mapper.LoginRequest
import com.example.watsapporderservices.data.mapper.RegisterRequest
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.data.security.JWT_AUTH_NAME
import com.example.watsapporderservices.data.security.TOKEN_TYPE
import com.example.watsapporderservices.data.security.USER_ID_CLAIM
import com.example.watsapporderservices.domain.usecase.AuthResult
import com.example.watsapporderservices.domain.usecase.AuthUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(useCase: AuthUseCase) {
    route("/auth") {
        post("/register") {
            respondAuth(call, useCase.register(call.receive<RegisterRequest>()))
        }
        post("/login") {
            respondAuth(call, useCase.login(call.receive<LoginRequest>()))
        }
        post("/federated") {
            respondAuth(call, useCase.federated(call.receive<FederatedAuthRequest>()))
        }
        authenticate(JWT_AUTH_NAME) {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
                    return@get
                }
                val userId = principal.payload.getClaim(USER_ID_CLAIM).asLong()?.toInt()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
                    return@get
                }
                val user = useCase.profile(userId)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, AuthErrorCode.USER_NOT_FOUND.toResponse())
                    return@get
                }
                call.respond(user)
            }
        }
    }
}

private suspend fun respondAuth(call: ApplicationCall, result: AuthResult) {
    when (result) {
        is AuthResult.Success -> call.respond(
            HttpStatusCode.OK,
            AuthResponse(
                token = result.token,
                tokenType = TOKEN_TYPE,
                expiresIn = result.expiresIn,
                user = result.user,
            ),
        )

        is AuthResult.InvalidInput -> call.respond(HttpStatusCode.BadRequest, result.code.toResponse())

        AuthResult.EmailAlreadyRegistered ->
            call.respond(HttpStatusCode.Conflict, AuthErrorCode.EMAIL_ALREADY_REGISTERED.toResponse())

        AuthResult.InvalidCredentials ->
            call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.INVALID_CREDENTIALS.toResponse())

        AuthResult.InvalidToken ->
            call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.INVALID_TOKEN.toResponse())
    }
}

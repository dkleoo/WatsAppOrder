package com.example.watsapporderservices.plugins

import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.data.security.JWT_AUTH_NAME
import com.example.watsapporderservices.data.security.TokenService
import com.example.watsapporderservices.data.security.USER_ID_CLAIM
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
}

fun Application.configureSecurity(tokenService: TokenService) {
    install(Authentication) {
        jwt(JWT_AUTH_NAME) {
            realm = tokenService.realm
            verifier(tokenService.verifier())
            validate { credential ->
                credential.payload.getClaim(USER_ID_CLAIM).asLong()?.let { JWTPrincipal(credential.payload) }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
            }
        }
    }
}

fun Application.configureStatusPages() {
    val logger = log
    install(StatusPages) {
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, AuthErrorCode.INVALID_REQUEST.toResponse())
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, AuthErrorCode.INVALID_REQUEST.toResponse())
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled server error", cause)
            call.respond(HttpStatusCode.InternalServerError, AuthErrorCode.INTERNAL_ERROR.toResponse())
        }
    }
}

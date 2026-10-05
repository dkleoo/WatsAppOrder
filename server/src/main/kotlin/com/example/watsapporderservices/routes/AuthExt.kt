package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.security.TokenService
import com.example.watsapporderservices.data.security.USER_ID_CLAIM
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

internal fun ApplicationCall.userId(): Int? =
    principal<JWTPrincipal>()?.payload?.getClaim(USER_ID_CLAIM)?.asLong()?.toInt()

/** Verifies a JWT received as a query parameter (used by the websocket, which cannot send headers). */
internal fun TokenService.userIdFromToken(token: String?): Int? {
    val value = token?.removePrefix("Bearer ")?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { verifier().verify(value).getClaim(USER_ID_CLAIM).asLong()?.toInt() }.getOrNull()
}

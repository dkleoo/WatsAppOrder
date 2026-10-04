package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.security.USER_ID_CLAIM
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

internal fun ApplicationCall.userId(): Int? =
    principal<JWTPrincipal>()?.payload?.getClaim(USER_ID_CLAIM)?.asLong()?.toInt()

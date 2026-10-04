package com.example.watsapporderservices.data.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

const val JWT_AUTH_NAME = "auth-jwt"
const val USER_ID_CLAIM = "userId"
const val EMAIL_CLAIM = "email"
const val STORE_ID_CLAIM = "storeId"
const val TOKEN_TYPE = "Bearer"

class TokenService(private val config: JwtConfig) {
    private val algorithm: Algorithm = Algorithm.HMAC256(config.secret)

    private val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    val realm: String get() = config.realm

    val expirationSeconds: Long get() = config.expirationMinutes * 60

    fun issue(userId: Int, email: String, storeId: Int? = null): String {
        val builder = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim(USER_ID_CLAIM, userId.toLong())
            .withClaim(EMAIL_CLAIM, email)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + config.expirationMillis))
        storeId?.let { builder.withClaim(STORE_ID_CLAIM, it.toLong()) }
        return builder.sign(algorithm)
    }

    fun verifier(): JWTVerifier = verifier
}

package com.example.watsapporderservices.data.security

import at.favre.lib.crypto.bcrypt.BCrypt

const val BCRYPT_COST = 12

class PasswordHasher(private val cost: Int = BCRYPT_COST) {
    fun hash(password: String): String = BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}

package com.eloiza.finrag.domain.port

interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(
        rawPassword: String,
        hashedPassword: String,
    ): Boolean
}

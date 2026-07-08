package com.eloiza.finrag.infrastructure.security

import com.eloiza.finrag.domain.port.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher(
    private val encoder: BCryptPasswordEncoder = BCryptPasswordEncoder(),
) : PasswordHasher {
    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)!!

    override fun matches(
        rawPassword: String,
        hashedPassword: String,
    ): Boolean = encoder.matches(rawPassword, hashedPassword)
}

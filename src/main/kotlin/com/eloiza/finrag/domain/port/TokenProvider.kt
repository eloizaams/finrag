package com.eloiza.finrag.domain.port

import com.eloiza.finrag.domain.model.User
import java.util.UUID

interface TokenProvider {
    fun generate(user: User): TokenResult

    fun validate(token: String): TokenClaims?
}

data class TokenResult(
    val token: String,
    val expiresInSeconds: Long,
)

data class TokenClaims(
    val userId: UUID,
    val email: String,
)

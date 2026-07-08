package com.eloiza.finrag.domain.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant,
) {
    init {
        require(email.isNotBlank()) { "email não pode ser vazio" }
        require(passwordHash.isNotBlank()) { "passwordHash não pode ser vazio" }
    }
}

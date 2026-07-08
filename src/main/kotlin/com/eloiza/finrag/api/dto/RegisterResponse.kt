package com.eloiza.finrag.api.dto

import java.util.UUID

data class RegisterResponse(
    val id: UUID,
    val email: String,
)

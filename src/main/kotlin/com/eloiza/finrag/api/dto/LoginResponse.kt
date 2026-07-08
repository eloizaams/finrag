package com.eloiza.finrag.api.dto

data class LoginResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)

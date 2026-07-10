package com.eloiza.finrag.domain.model

data class LlmResponse(
    val text: String,
    val promptTokens: Int,
    val completionTokens: Int,
) {
    init {
        require(text.isNotBlank()) { "text não pode ser vazio" }
    }
}

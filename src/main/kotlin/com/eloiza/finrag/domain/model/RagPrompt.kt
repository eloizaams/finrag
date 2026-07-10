package com.eloiza.finrag.domain.model

data class RagPrompt(
    val system: String,
    val user: String,
) {
    init {
        require(system.isNotBlank()) { "system não pode ser vazio" }
        require(user.isNotBlank()) { "user não pode ser vazio" }
    }
}

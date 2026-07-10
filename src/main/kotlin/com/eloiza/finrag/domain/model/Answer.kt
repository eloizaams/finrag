package com.eloiza.finrag.domain.model

data class Answer(
    val text: String,
    val sources: List<ScoredChunk>,
) {
    init {
        require(text.isNotBlank()) { "text não pode ser vazio" }
    }
}

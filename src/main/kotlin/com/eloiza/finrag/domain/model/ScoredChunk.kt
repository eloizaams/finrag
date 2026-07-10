package com.eloiza.finrag.domain.model

import java.util.UUID

data class ScoredChunk(
    val chunkId: UUID,
    val documentId: UUID,
    val filename: String,
    val content: String,
    val similarity: Double,
) {
    init {
        require(filename.isNotBlank()) { "filename não pode ser vazio" }
        require(content.isNotBlank()) { "content não pode ser vazio" }
        require(similarity in -1.0..1.0) {
            "similarity precisa estar entre -1.0 e 1.0, era $similarity"
        }
    }
}

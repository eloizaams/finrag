package com.eloiza.finrag.domain.model

import java.util.UUID

data class Source(
    val documentId: UUID,
    val filename: String,
    val excerpt: String,
    val similarity: Double,
) {
    init {
        require(filename.isNotBlank()) { "filename não pode ser vazio" }
        require(excerpt.isNotBlank()) { "excerpt não pode ser vazio" }
        require(similarity in -1.0..1.0) {
            "similarity precisa estar entre -1.0 e 1.0, era $similarity"
        }
    }

    companion object {
        fun from(chunk: ScoredChunk) =
            Source(
                documentId = chunk.documentId,
                filename = chunk.filename,
                excerpt = chunk.content,
                similarity = chunk.similarity,
            )
    }
}

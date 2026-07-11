package com.eloiza.finrag.domain.model

import java.util.UUID

data class Chunk(
    val id: UUID,
    val documentId: UUID,
    val index: Int,
    val content: String,
    val embedding: List<Float>,
) {
    init {
        require(content.isNotBlank()) { "content não pode ser vazio" }
        require(embedding.size == EMBEDDING_DIMENSIONS) {
            "embedding precisa ter $EMBEDDING_DIMENSIONS dimensões, tinha ${embedding.size}"
        }
    }

    companion object {
        const val EMBEDDING_DIMENSIONS = 1536
    }
}

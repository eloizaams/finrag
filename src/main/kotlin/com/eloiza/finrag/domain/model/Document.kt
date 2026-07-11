package com.eloiza.finrag.domain.model

import java.time.Instant
import java.util.UUID

data class Document(
    val id: UUID,
    val userId: UUID,
    val filename: String,
    val chunkCount: Int,
    val createdAt: Instant,
) {
    init {
        require(filename.isNotBlank()) { "filename não pode ser vazio" }
        require(chunkCount > 0) { "chunkCount precisa ser positivo" }
    }
}

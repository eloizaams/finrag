package com.eloiza.finrag.api.dto

import com.eloiza.finrag.domain.model.Document
import java.time.Instant
import java.util.UUID

data class DocumentResponse(
    val id: UUID,
    val filename: String,
    val chunkCount: Int,
    val createdAt: Instant,
) {
    companion object {
        fun from(document: Document) =
            DocumentResponse(
                id = document.id,
                filename = document.filename,
                chunkCount = document.chunkCount,
                createdAt = document.createdAt,
            )
    }
}

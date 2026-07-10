package com.eloiza.finrag.api.dto

import com.eloiza.finrag.domain.model.ScoredChunk
import java.util.UUID

data class SourceResponse(
    val documentId: UUID,
    val filename: String,
    val excerpt: String,
    val similarity: Double,
) {
    companion object {
        const val EXCERPT_MAX_CHARS = 200

        fun from(chunk: ScoredChunk) =
            SourceResponse(
                documentId = chunk.documentId,
                filename = chunk.filename,
                excerpt = chunk.content.toExcerpt(),
                similarity = chunk.similarity,
            )

        private fun String.toExcerpt(): String = if (length <= EXCERPT_MAX_CHARS) this else take(EXCERPT_MAX_CHARS) + "…"
    }
}

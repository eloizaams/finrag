package com.eloiza.finrag.api.dto

import com.eloiza.finrag.domain.model.Source
import java.util.UUID

data class SourceResponse(
    val documentId: UUID,
    val filename: String,
    val excerpt: String,
    val similarity: Double,
) {
    companion object {
        fun from(source: Source) =
            SourceResponse(
                documentId = source.documentId,
                filename = source.filename,
                excerpt = source.excerpt,
                similarity = source.similarity,
            )
    }
}

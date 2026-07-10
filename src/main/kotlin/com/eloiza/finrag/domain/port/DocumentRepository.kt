package com.eloiza.finrag.domain.port

import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import java.util.UUID

interface DocumentRepository {
    fun save(
        document: Document,
        chunks: List<Chunk>,
    ): Document

    fun findAllByUserId(userId: UUID): List<Document>
}

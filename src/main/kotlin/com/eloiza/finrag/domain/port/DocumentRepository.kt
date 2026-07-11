package com.eloiza.finrag.domain.port

import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.model.PageResult
import java.util.UUID

interface DocumentRepository {
    fun save(
        document: Document,
        chunks: List<Chunk>,
    ): Document

    fun findAllByUserId(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResult<Document>

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Document?

    fun deleteByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Boolean
}

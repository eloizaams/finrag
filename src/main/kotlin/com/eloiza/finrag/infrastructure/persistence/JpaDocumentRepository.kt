package com.eloiza.finrag.infrastructure.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaDocumentRepository : JpaRepository<DocumentEntity, UUID> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<DocumentEntity>

    fun findAllByUserIdOrderByCreatedAtDesc(
        userId: UUID,
        pageable: Pageable,
    ): Page<DocumentEntity>

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): DocumentEntity?

    fun deleteByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Long
}

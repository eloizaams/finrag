package com.eloiza.finrag.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaDocumentRepository : JpaRepository<DocumentEntity, UUID> {
    fun findAllByUserId(userId: UUID): List<DocumentEntity>
}

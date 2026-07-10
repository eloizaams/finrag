package com.eloiza.finrag.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(nullable = false)
    val filename: String,
    @Column(name = "chunk_count", nullable = false)
    val chunkCount: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

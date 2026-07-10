package com.eloiza.finrag.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Array
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "chunks")
class ChunkEntity(
    @Id
    val id: UUID,
    @Column(name = "document_id", nullable = false)
    val documentId: UUID,
    @Column(name = "chunk_index", nullable = false)
    val index: Int,
    @Column(nullable = false)
    val content: String,
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(nullable = false)
    val embedding: FloatArray,
)

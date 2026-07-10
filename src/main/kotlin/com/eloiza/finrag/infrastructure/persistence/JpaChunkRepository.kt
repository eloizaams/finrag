package com.eloiza.finrag.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface JpaChunkRepository : JpaRepository<ChunkEntity, UUID> {
    @Query(
        value = """
            SELECT c.id AS chunkId,
                   c.document_id AS documentId,
                   d.filename AS filename,
                   c.content AS content,
                   1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
            FROM chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE d.user_id = :userId
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :k
        """,
        nativeQuery = true,
    )
    fun findMostSimilar(
        @Param("userId") userId: UUID,
        @Param("queryEmbedding") queryEmbedding: String,
        @Param("k") k: Int,
    ): List<ChunkSimilarityProjection>
}

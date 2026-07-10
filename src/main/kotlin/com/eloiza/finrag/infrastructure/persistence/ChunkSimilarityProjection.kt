package com.eloiza.finrag.infrastructure.persistence

import java.util.UUID

interface ChunkSimilarityProjection {
    val chunkId: UUID
    val documentId: UUID
    val filename: String
    val content: String
    val similarity: Double
}

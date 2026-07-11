package com.eloiza.finrag.domain.port

import com.eloiza.finrag.domain.model.ScoredChunk
import java.util.UUID

interface ChunkSearchRepository {
    fun findMostSimilar(
        userId: UUID,
        queryEmbedding: List<Float>,
        k: Int,
    ): List<ScoredChunk>
}

package com.eloiza.finrag.infrastructure.persistence

import com.eloiza.finrag.domain.model.ScoredChunk
import com.eloiza.finrag.domain.port.ChunkSearchRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChunkSearchRepositoryJpaAdapter(
    private val jpaChunkRepository: JpaChunkRepository,
) : ChunkSearchRepository {
    override fun findMostSimilar(
        userId: UUID,
        queryEmbedding: List<Float>,
        k: Int,
    ): List<ScoredChunk> =
        jpaChunkRepository
            .findMostSimilar(userId, queryEmbedding.toVectorLiteral(), k)
            .map { it.toDomain() }
}

private fun List<Float>.toVectorLiteral(): String = joinToString(prefix = "[", postfix = "]")

private fun ChunkSimilarityProjection.toDomain() =
    ScoredChunk(
        chunkId = chunkId,
        documentId = documentId,
        filename = filename,
        content = content,
        similarity = similarity,
    )

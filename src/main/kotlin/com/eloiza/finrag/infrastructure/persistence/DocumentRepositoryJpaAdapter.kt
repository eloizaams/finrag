package com.eloiza.finrag.infrastructure.persistence

import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.port.DocumentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class DocumentRepositoryJpaAdapter(
    private val jpaDocumentRepository: JpaDocumentRepository,
    private val jpaChunkRepository: JpaChunkRepository,
) : DocumentRepository {
    @Transactional
    override fun save(
        document: Document,
        chunks: List<Chunk>,
    ): Document {
        jpaDocumentRepository.save(document.toEntity())
        jpaChunkRepository.saveAll(chunks.map { it.toEntity(document.userId) })
        return document
    }

    override fun findAllByUserId(userId: UUID): List<Document> =
        jpaDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(userId).map { it.toDomain() }

    override fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Document? = jpaDocumentRepository.findByIdAndUserId(id, userId)?.toDomain()

    // Os chunks são removidos pelo ON DELETE CASCADE da FK (migration V3)
    @Transactional
    override fun deleteByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Boolean = jpaDocumentRepository.deleteByIdAndUserId(id, userId) > 0
}

private fun Document.toEntity() =
    DocumentEntity(
        id = id,
        userId = userId,
        filename = filename,
        chunkCount = chunkCount,
        createdAt = createdAt,
    )

private fun DocumentEntity.toDomain() =
    Document(
        id = id,
        userId = userId,
        filename = filename,
        chunkCount = chunkCount,
        createdAt = createdAt,
    )

private fun Chunk.toEntity(userId: UUID) =
    ChunkEntity(
        id = id,
        documentId = documentId,
        userId = userId,
        index = index,
        content = content,
        embedding = embedding.toFloatArray(),
    )

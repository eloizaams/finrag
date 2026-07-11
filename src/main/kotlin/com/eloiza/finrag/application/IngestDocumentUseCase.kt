package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.EmptyDocumentException
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.model.DocumentType
import com.eloiza.finrag.domain.port.DocumentRepository
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.PipelineMetrics
import com.eloiza.finrag.domain.port.TextExtractor
import com.eloiza.finrag.domain.service.TextChunker
import java.time.Instant
import java.util.UUID

class IngestDocumentUseCase(
    private val textExtractor: TextExtractor,
    private val textChunker: TextChunker,
    private val embeddingProvider: EmbeddingProvider,
    private val documentRepository: DocumentRepository,
    private val pipelineMetrics: PipelineMetrics,
) {
    fun ingest(
        userId: UUID,
        filename: String,
        bytes: ByteArray,
    ): Document {
        val type = DocumentType.fromFilename(filename)
        val text =
            pipelineMetrics.recordDuration(PIPELINE, STAGE_EXTRACTION) {
                textExtractor.extract(bytes, type)
            }
        if (text.isBlank()) {
            throw EmptyDocumentException()
        }

        val chunkContents =
            pipelineMetrics.recordDuration(PIPELINE, STAGE_CHUNKING) {
                textChunker.chunk(text)
            }
        val embeddings =
            pipelineMetrics.recordDuration(PIPELINE, STAGE_EMBEDDING) {
                embeddingProvider.embed(chunkContents)
            }

        val documentId = UUID.randomUUID()
        val chunks =
            chunkContents.mapIndexed { index, content ->
                Chunk(
                    id = UUID.randomUUID(),
                    documentId = documentId,
                    index = index,
                    content = content,
                    embedding = embeddings[index],
                )
            }
        val document =
            Document(
                id = documentId,
                userId = userId,
                filename = filename,
                chunkCount = chunks.size,
                createdAt = Instant.now(),
            )

        return documentRepository.save(document, chunks)
    }

    private companion object {
        const val PIPELINE = "ingestion"
        const val STAGE_EXTRACTION = "extraction"
        const val STAGE_CHUNKING = "chunking"
        const val STAGE_EMBEDDING = "embedding"
    }
}

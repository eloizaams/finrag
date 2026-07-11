package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.model.DocumentType
import com.eloiza.finrag.domain.model.PageResult
import com.eloiza.finrag.domain.port.DocumentRepository
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.TextExtractor
import java.util.UUID

class FakeTextExtractor(
    private val textToReturn: String,
) : TextExtractor {
    override fun extract(
        bytes: ByteArray,
        type: DocumentType,
    ): String = textToReturn
}

class FakeEmbeddingProvider(
    private val shouldFail: Boolean = false,
) : EmbeddingProvider {
    override fun embed(texts: List<String>): List<List<Float>> {
        if (shouldFail) {
            throw EmbeddingProviderException("falha simulada do provedor de embeddings", provider = "openai")
        }
        return texts.map { text -> List(Chunk.EMBEDDING_DIMENSIONS) { text.length.toFloat() } }
    }
}

class FakeDocumentRepository : DocumentRepository {
    val saved = mutableListOf<Pair<Document, List<Chunk>>>()
    private val documentsByUserId = mutableMapOf<UUID, MutableList<Document>>()

    override fun save(
        document: Document,
        chunks: List<Chunk>,
    ): Document {
        saved.add(document to chunks)
        documentsByUserId.getOrPut(document.userId) { mutableListOf() }.add(document)
        return document
    }

    override fun findAllByUserId(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResult<Document> {
        val all = documentsByUserId[userId].orEmpty().sortedByDescending { it.createdAt }
        return PageResult(
            items = all.drop(page * size).take(size),
            page = page,
            size = size,
            totalItems = all.size.toLong(),
        )
    }

    override fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Document? = documentsByUserId[userId].orEmpty().find { it.id == id }

    override fun deleteByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Boolean = documentsByUserId[userId]?.removeIf { it.id == id } ?: false
}

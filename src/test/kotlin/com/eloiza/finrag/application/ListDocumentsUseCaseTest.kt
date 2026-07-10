package com.eloiza.finrag.application

import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class ListDocumentsUseCaseTest :
    FunSpec({
        test("retorna somente os documentos do usuário informado") {
            val documentRepository = FakeDocumentRepository()
            val userId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()

            val ownDocument = anyDocument(userId)
            documentRepository.save(ownDocument, listOf(anyChunk(ownDocument.id)))
            documentRepository.save(anyDocument(otherUserId), listOf(anyChunk(otherUserId)))

            val useCase = ListDocumentsUseCase(documentRepository)

            useCase.list(userId) shouldBe listOf(ownDocument)
        }

        test("retorna lista vazia quando o usuário não tem documentos") {
            val useCase = ListDocumentsUseCase(FakeDocumentRepository())

            useCase.list(UUID.randomUUID()).shouldBeEmpty()
        }
    })

private fun anyDocument(userId: UUID): Document =
    Document(
        id = UUID.randomUUID(),
        userId = userId,
        filename = "relatorio.pdf",
        chunkCount = 1,
        createdAt = Instant.now(),
    )

private fun anyChunk(documentId: UUID): Chunk =
    Chunk(
        id = UUID.randomUUID(),
        documentId = documentId,
        index = 0,
        content = "conteúdo",
        embedding = List(Chunk.EMBEDDING_DIMENSIONS) { 0f },
    )

package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.DocumentNotFoundException
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class DeleteDocumentUseCaseTest :
    FunSpec({
        test("remove o documento quando ele pertence ao usuário") {
            val documentRepository = FakeDocumentRepository()
            val userId = UUID.randomUUID()
            val document = anyDocument(userId)
            documentRepository.save(document, listOf(anyChunk(document.id)))

            val useCase = DeleteDocumentUseCase(documentRepository)
            useCase.delete(userId, document.id)

            documentRepository.findAllByUserId(userId, page = 0, size = 20).items.shouldBeEmpty()
        }

        test("lança DocumentNotFoundException para documento inexistente") {
            val useCase = DeleteDocumentUseCase(FakeDocumentRepository())

            shouldThrow<DocumentNotFoundException> {
                useCase.delete(UUID.randomUUID(), UUID.randomUUID())
            }
        }

        test("lança DocumentNotFoundException para documento de outro usuário, sem removê-lo") {
            val documentRepository = FakeDocumentRepository()
            val ownerId = UUID.randomUUID()
            val document = anyDocument(ownerId)
            documentRepository.save(document, listOf(anyChunk(document.id)))

            val useCase = DeleteDocumentUseCase(documentRepository)

            shouldThrow<DocumentNotFoundException> {
                useCase.delete(UUID.randomUUID(), document.id)
            }
            documentRepository.findAllByUserId(ownerId, page = 0, size = 20).items shouldBe listOf(document)
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

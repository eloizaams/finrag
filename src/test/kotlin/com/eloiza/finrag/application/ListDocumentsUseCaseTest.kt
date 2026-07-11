package com.eloiza.finrag.application

import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
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

            useCase.list(userId, page = 0, size = 20).items shouldBe listOf(ownDocument)
        }

        test("retorna página vazia quando o usuário não tem documentos") {
            val useCase = ListDocumentsUseCase(FakeDocumentRepository())

            val result = useCase.list(UUID.randomUUID(), page = 0, size = 20)

            result.items.shouldBeEmpty()
            result.totalItems shouldBe 0
            result.totalPages shouldBe 0
        }

        test("pagina os resultados com totalItems e totalPages corretos") {
            val documentRepository = FakeDocumentRepository()
            val userId = UUID.randomUUID()
            repeat(3) {
                val document = anyDocument(userId)
                documentRepository.save(document, listOf(anyChunk(document.id)))
            }

            val useCase = ListDocumentsUseCase(documentRepository)
            val firstPage = useCase.list(userId, page = 0, size = 2)
            val secondPage = useCase.list(userId, page = 1, size = 2)

            firstPage.items shouldHaveSize 2
            firstPage.totalItems shouldBe 3
            firstPage.totalPages shouldBe 2
            secondPage.items shouldHaveSize 1
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

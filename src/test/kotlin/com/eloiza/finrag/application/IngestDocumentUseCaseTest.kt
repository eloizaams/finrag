package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.exception.EmptyDocumentException
import com.eloiza.finrag.domain.exception.UnsupportedDocumentTypeException
import com.eloiza.finrag.domain.service.TextChunker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.UUID

class IngestDocumentUseCaseTest :
    FunSpec({
        val userId = UUID.randomUUID()

        test("ingere um documento com sucesso: extrai, chunka, gera embeddings e persiste tudo de uma vez") {
            val documentRepository = FakeDocumentRepository()
            val useCase =
                IngestDocumentUseCase(
                    textExtractor = FakeTextExtractor("Primeiro paragrafo.\n\nSegundo paragrafo."),
                    textChunker = TextChunker(maxChars = 100, overlapChars = 10),
                    embeddingProvider = FakeEmbeddingProvider(),
                    documentRepository = documentRepository,
                )

            val document = useCase.ingest(userId, "relatorio.pdf", ByteArray(0))

            document.userId shouldBe userId
            document.filename shouldBe "relatorio.pdf"
            document.chunkCount shouldBe 1
            documentRepository.saved shouldHaveSize 1
            documentRepository.saved.first().second shouldHaveSize 1
        }

        test("extensão não suportada lança UnsupportedDocumentTypeException e não chama o repositório") {
            val documentRepository = FakeDocumentRepository()
            val useCase =
                IngestDocumentUseCase(
                    textExtractor = FakeTextExtractor("texto qualquer"),
                    textChunker = TextChunker(),
                    embeddingProvider = FakeEmbeddingProvider(),
                    documentRepository = documentRepository,
                )

            shouldThrow<UnsupportedDocumentTypeException> {
                useCase.ingest(userId, "planilha.xlsx", ByteArray(0))
            }
            documentRepository.saved.shouldBeEmpty()
        }

        test("texto em branco extraído lança EmptyDocumentException e não chama o repositório") {
            val documentRepository = FakeDocumentRepository()
            val useCase =
                IngestDocumentUseCase(
                    textExtractor = FakeTextExtractor("   \n\n   \n"),
                    textChunker = TextChunker(),
                    embeddingProvider = FakeEmbeddingProvider(),
                    documentRepository = documentRepository,
                )

            shouldThrow<EmptyDocumentException> {
                useCase.ingest(userId, "relatorio.md", ByteArray(0))
            }
            documentRepository.saved.shouldBeEmpty()
        }

        test("falha do provedor de embeddings propaga a exceção e não persiste nada (atomicidade)") {
            val documentRepository = FakeDocumentRepository()
            val useCase =
                IngestDocumentUseCase(
                    textExtractor = FakeTextExtractor("texto válido para gerar chunk"),
                    textChunker = TextChunker(),
                    embeddingProvider = FakeEmbeddingProvider(shouldFail = true),
                    documentRepository = documentRepository,
                )

            shouldThrow<EmbeddingProviderException> {
                useCase.ingest(userId, "relatorio.pdf", ByteArray(0))
            }
            documentRepository.saved.shouldBeEmpty()
        }
    })

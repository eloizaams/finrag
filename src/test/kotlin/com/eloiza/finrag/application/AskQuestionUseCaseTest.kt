package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.exception.LlmProviderException
import com.eloiza.finrag.domain.model.ScoredChunk
import com.eloiza.finrag.domain.service.RagPromptBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class AskQuestionUseCaseTest :
    FunSpec({
        val userId = UUID.randomUUID()

        fun scoredChunk(
            filename: String = "relatorio.pdf",
            content: String = "conteúdo relevante do chunk",
            similarity: Double = 0.9,
        ) = ScoredChunk(
            chunkId = UUID.randomUUID(),
            documentId = UUID.randomUUID(),
            filename = filename,
            content = content,
            similarity = similarity,
        )

        test("fluxo feliz: busca encontra chunks, LLM gera resposta e as fontes vêm dos chunks recuperados") {
            val chunk = scoredChunk(filename = "relatorio-q3.pdf", similarity = 0.87)
            val chunkSearchRepository = FakeChunkSearchRepository(chunksToReturn = listOf(chunk))
            val llmClient = FakeLlmClient(textToReturn = "A receita foi de R\$ 10 milhões [relatorio-q3.pdf].")
            val useCase =
                AskQuestionUseCase(
                    embeddingProvider = FakeEmbeddingProvider(),
                    chunkSearchRepository = chunkSearchRepository,
                    ragPromptBuilder = RagPromptBuilder(),
                    llmClient = llmClient,
                    topK = 5,
                    minSimilarity = 0.25,
                )

            val answer = useCase.ask(userId, "Qual foi a receita no Q3?")

            answer.text shouldBe "A receita foi de R\$ 10 milhões [relatorio-q3.pdf]."
            answer.sources shouldBe listOf(chunk)
            chunkSearchRepository.lastUserId shouldBe userId
            chunkSearchRepository.lastK shouldBe 5
        }

        test("busca sem resultados devolve resposta padrão sem chamar o LLM") {
            val llmClient = FakeLlmClient()
            val useCase =
                AskQuestionUseCase(
                    embeddingProvider = FakeEmbeddingProvider(),
                    chunkSearchRepository = FakeChunkSearchRepository(chunksToReturn = emptyList()),
                    ragPromptBuilder = RagPromptBuilder(),
                    llmClient = llmClient,
                    topK = 5,
                    minSimilarity = 0.25,
                )

            val answer = useCase.ask(userId, "Pergunta sem contexto indexado")

            answer.text shouldBe "Os documentos indexados não contêm informação relevante para responder a essa pergunta."
            answer.sources.shouldBeEmpty()
            llmClient.called shouldBe false
        }

        test("chunks abaixo do threshold de similaridade são descartados e não vão para o LLM") {
            val relevant = scoredChunk(filename = "relevante.pdf", similarity = 0.6)
            val irrelevant = scoredChunk(filename = "irrelevante.pdf", similarity = 0.1)
            val llmClient = FakeLlmClient(textToReturn = "resposta com base no chunk relevante")
            val useCase =
                AskQuestionUseCase(
                    embeddingProvider = FakeEmbeddingProvider(),
                    chunkSearchRepository = FakeChunkSearchRepository(chunksToReturn = listOf(relevant, irrelevant)),
                    ragPromptBuilder = RagPromptBuilder(),
                    llmClient = llmClient,
                    topK = 5,
                    minSimilarity = 0.25,
                )

            val answer = useCase.ask(userId, "Qualquer pergunta")

            answer.sources shouldBe listOf(relevant)
        }

        test("somente chunks irrelevantes devolve resposta padrão sem chamar o LLM") {
            val llmClient = FakeLlmClient()
            val useCase =
                AskQuestionUseCase(
                    embeddingProvider = FakeEmbeddingProvider(),
                    chunkSearchRepository = FakeChunkSearchRepository(chunksToReturn = listOf(scoredChunk(similarity = 0.1))),
                    ragPromptBuilder = RagPromptBuilder(),
                    llmClient = llmClient,
                    topK = 5,
                    minSimilarity = 0.25,
                )

            val answer = useCase.ask(userId, "Pergunta sobre assunto não indexado")

            answer.text shouldBe "Os documentos indexados não contêm informação relevante para responder a essa pergunta."
            answer.sources.shouldBeEmpty()
            llmClient.called shouldBe false
        }

        test("falha do provedor de embeddings propaga a exceção e não chama busca nem LLM") {
            val chunkSearchRepository = FakeChunkSearchRepository()
            val llmClient = FakeLlmClient()
            val useCase =
                AskQuestionUseCase(
                    embeddingProvider = FakeEmbeddingProvider(shouldFail = true),
                    chunkSearchRepository = chunkSearchRepository,
                    ragPromptBuilder = RagPromptBuilder(),
                    llmClient = llmClient,
                    topK = 5,
                    minSimilarity = 0.25,
                )

            shouldThrow<EmbeddingProviderException> {
                useCase.ask(userId, "Qualquer pergunta")
            }
            chunkSearchRepository.lastUserId.shouldBeNull()
            llmClient.called shouldBe false
        }

        test("falha do provedor de LLM propaga a exceção") {
            val useCase =
                AskQuestionUseCase(
                    embeddingProvider = FakeEmbeddingProvider(),
                    chunkSearchRepository = FakeChunkSearchRepository(chunksToReturn = listOf(scoredChunk())),
                    ragPromptBuilder = RagPromptBuilder(),
                    llmClient = FakeLlmClient(shouldFail = true),
                    topK = 5,
                    minSimilarity = 0.25,
                )

            shouldThrow<LlmProviderException> {
                useCase.ask(userId, "Qualquer pergunta")
            }
        }
    })

package com.eloiza.finrag.application

import com.eloiza.finrag.domain.model.Answer
import com.eloiza.finrag.domain.port.ChunkSearchRepository
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.LlmClient
import com.eloiza.finrag.domain.service.RagPromptBuilder
import java.util.UUID

class AskQuestionUseCase(
    private val embeddingProvider: EmbeddingProvider,
    private val chunkSearchRepository: ChunkSearchRepository,
    private val ragPromptBuilder: RagPromptBuilder,
    private val llmClient: LlmClient,
    private val topK: Int = 5,
    private val minSimilarity: Double = 0.25,
) {
    fun ask(
        userId: UUID,
        question: String,
    ): Answer {
        val queryEmbedding = embeddingProvider.embed(listOf(question)).first()
        val chunks =
            chunkSearchRepository
                .findMostSimilar(userId, queryEmbedding, topK)
                .filter { it.similarity >= minSimilarity }
        if (chunks.isEmpty()) {
            return Answer(text = NO_CONTEXT_ANSWER, sources = emptyList())
        }

        val prompt = ragPromptBuilder.build(question, chunks)
        val text = llmClient.generate(prompt.system, prompt.user)

        return Answer(text = text, sources = chunks)
    }

    private companion object {
        const val NO_CONTEXT_ANSWER = "Os documentos indexados não contêm informação relevante para responder a essa pergunta."
    }
}

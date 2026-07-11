package com.eloiza.finrag.application

import com.eloiza.finrag.domain.model.Answer
import com.eloiza.finrag.domain.port.ChunkSearchRepository
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.LlmClient
import com.eloiza.finrag.domain.port.PipelineMetrics
import com.eloiza.finrag.domain.service.RagPromptBuilder
import java.util.UUID

class AskQuestionUseCase(
    private val embeddingProvider: EmbeddingProvider,
    private val chunkSearchRepository: ChunkSearchRepository,
    private val ragPromptBuilder: RagPromptBuilder,
    private val llmClient: LlmClient,
    private val pipelineMetrics: PipelineMetrics,
    private val topK: Int,
    private val minSimilarity: Double,
) {
    fun ask(
        userId: UUID,
        question: String,
    ): Answer {
        val queryEmbedding =
            pipelineMetrics.recordDuration(PIPELINE, STAGE_EMBEDDING) {
                embeddingProvider.embed(listOf(question)).first()
            }
        val chunks =
            pipelineMetrics
                .recordDuration(PIPELINE, STAGE_SEARCH) {
                    chunkSearchRepository.findMostSimilar(userId, queryEmbedding, topK)
                }.filter { it.similarity >= minSimilarity }
        if (chunks.isEmpty()) {
            return Answer(text = NO_CONTEXT_ANSWER, sources = emptyList())
        }

        val prompt = ragPromptBuilder.build(question, chunks)
        val response =
            pipelineMetrics.recordDuration(PIPELINE, STAGE_LLM) {
                llmClient.generate(prompt.system, prompt.user)
            }
        pipelineMetrics.recordTokens(response.promptTokens, response.completionTokens)

        return Answer(text = response.text, sources = chunks)
    }

    private companion object {
        const val NO_CONTEXT_ANSWER = "Os documentos indexados não contêm informação relevante para responder a essa pergunta."
        const val PIPELINE = "question"
        const val STAGE_EMBEDDING = "embedding"
        const val STAGE_SEARCH = "search"
        const val STAGE_LLM = "llm"
    }
}

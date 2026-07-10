package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.LlmProviderException
import com.eloiza.finrag.domain.model.ScoredChunk
import com.eloiza.finrag.domain.port.ChunkSearchRepository
import com.eloiza.finrag.domain.port.LlmClient
import java.util.UUID

class FakeChunkSearchRepository(
    private val chunksToReturn: List<ScoredChunk> = emptyList(),
) : ChunkSearchRepository {
    var lastUserId: UUID? = null
        private set
    var lastK: Int? = null
        private set

    override fun findMostSimilar(
        userId: UUID,
        queryEmbedding: List<Float>,
        k: Int,
    ): List<ScoredChunk> {
        lastUserId = userId
        lastK = k
        return chunksToReturn
    }
}

class FakeLlmClient(
    private val textToReturn: String = "resposta gerada pelo LLM",
    private val shouldFail: Boolean = false,
) : LlmClient {
    var called: Boolean = false
        private set

    override fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): String {
        called = true
        if (shouldFail) {
            throw LlmProviderException("falha simulada do provedor de LLM")
        }
        return textToReturn
    }
}

package com.eloiza.finrag.infrastructure.openai

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.port.EmbeddingProvider
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class OpenAiEmbeddingProvider(
    restClientBuilder: RestClient.Builder,
    private val properties: OpenAiProperties,
) : EmbeddingProvider {
    private val restClient = restClientBuilder.baseUrl(properties.baseUrl).build()

    override fun embed(texts: List<String>): List<List<Float>> = texts.chunked(MAX_BATCH_SIZE).flatMap { batch -> embedBatch(batch) }

    private fun embedBatch(texts: List<String>): List<List<Float>> {
        try {
            val response =
                restClient
                    .post()
                    .uri("/v1/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                    .body(EmbeddingRequest(model = properties.embeddingModel, input = texts))
                    .retrieve()
                    .body<EmbeddingResponse>()
                    ?: throw EmbeddingProviderException("Resposta vazia da API de embeddings da OpenAI")

            if (response.data.size != texts.size) {
                throw EmbeddingProviderException(
                    "OpenAI retornou ${response.data.size} embeddings para ${texts.size} textos enviados",
                )
            }

            return response.data.sortedBy { it.index }.map { it.embedding }
        } catch (e: RestClientException) {
            throw EmbeddingProviderException("Falha ao chamar a API de embeddings da OpenAI", e)
        }
    }

    private companion object {
        const val MAX_BATCH_SIZE = 2048
    }
}

private data class EmbeddingRequest(
    val model: String,
    val input: List<String>,
)

private data class EmbeddingResponse(
    val data: List<EmbeddingData>,
)

private data class EmbeddingData(
    val embedding: List<Float>,
    val index: Int,
)

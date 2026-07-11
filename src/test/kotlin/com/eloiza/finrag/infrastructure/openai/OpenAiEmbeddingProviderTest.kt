package com.eloiza.finrag.infrastructure.openai

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.model.Chunk
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenAiEmbeddingProviderTest :
    FunSpec({
        val properties =
            OpenAiProperties(
                apiKey = "sk-test-123",
                baseUrl = "https://api.openai.test",
                embeddingModel = "text-embedding-3-small",
            )

        fun buildProvider(): Pair<OpenAiEmbeddingProvider, MockRestServiceServer> {
            val builder = RestClient.builder()
            val server = MockRestServiceServer.bindTo(builder).build()
            return OpenAiEmbeddingProvider(builder, properties) to server
        }

        fun fakeEmbeddingJson(seed: Float) = (0 until Chunk.EMBEDDING_DIMENSIONS).joinToString(",", "[", "]") { seed.toString() }

        test("envia todos os textos em uma única requisição em lote e retorna os embeddings na ordem dos inputs") {
            val (provider, server) = buildProvider()
            val responseJson =
                """
                {
                  "data": [
                    { "embedding": ${fakeEmbeddingJson(0.3f)}, "index": 1 },
                    { "embedding": ${fakeEmbeddingJson(0.1f)}, "index": 0 }
                  ]
                }
                """.trimIndent()

            server
                .expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test-123"))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

            val embeddings = provider.embed(listOf("texto 1", "texto 2"))

            embeddings shouldHaveSize 2
            embeddings[0] shouldBe List(Chunk.EMBEDDING_DIMENSIONS) { 0.1f }
            embeddings[1] shouldBe List(Chunk.EMBEDDING_DIMENSIONS) { 0.3f }
            server.verify()
        }

        test("erro HTTP da OpenAI lança EmbeddingProviderException") {
            val (provider, server) = buildProvider()

            server
                .expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            shouldThrow<EmbeddingProviderException> {
                provider.embed(listOf("texto"))
            }
        }

        test("embedding com dimensão diferente de 1536 lança EmbeddingProviderException") {
            val (provider, server) = buildProvider()
            val responseJson = """{ "data": [ { "embedding": [0.1, 0.2], "index": 0 } ] }"""

            server
                .expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

            shouldThrow<EmbeddingProviderException> {
                provider.embed(listOf("texto"))
            }
        }
    })

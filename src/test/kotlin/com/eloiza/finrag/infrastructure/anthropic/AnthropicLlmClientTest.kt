package com.eloiza.finrag.infrastructure.anthropic

import com.eloiza.finrag.domain.exception.LlmProviderException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
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

class AnthropicLlmClientTest :
    FunSpec({
        val properties =
            AnthropicProperties(
                apiKey = "sk-ant-test-123",
                baseUrl = "https://api.anthropic.test",
                model = "claude-haiku-4-5",
                maxTokens = 1024,
            )

        fun buildClient(): Pair<AnthropicLlmClient, MockRestServiceServer> {
            val builder = RestClient.builder()
            val server = MockRestServiceServer.bindTo(builder).build()
            return AnthropicLlmClient(builder, properties) to server
        }

        test("envia model, max_tokens, system, mensagem do usuário e a chave só no header — nunca no corpo") {
            val (client, server) = buildClient()
            val responseJson =
                """
                {
                  "content": [ { "type": "text", "text": "A receita foi de R${'$'} 10 milhões." } ],
                  "usage": { "input_tokens": 120, "output_tokens": 15 }
                }
                """.trimIndent()

            server
                .expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "sk-ant-test-123"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-haiku-4-5"))
                .andExpect(jsonPath("$.max_tokens").value(1024))
                .andExpect(jsonPath("$.system").value("responda com base no contexto"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Qual foi a receita?"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

            val response = client.generate("responda com base no contexto", "Qual foi a receita?")

            response.text shouldBe "A receita foi de R\$ 10 milhões."
            response.promptTokens shouldBe 120
            response.completionTokens shouldBe 15
            server.verify()
        }

        test("resposta sem campo usage devolve contagem de tokens zerada") {
            val (client, server) = buildClient()
            val responseJson = """{ "content": [ { "type": "text", "text": "Resposta sem usage" } ] }"""

            server
                .expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

            val response = client.generate("system", "pergunta")

            response.promptTokens shouldBe 0
            response.completionTokens shouldBe 0
        }

        test("erro HTTP da Anthropic lança LlmProviderException") {
            val (client, server) = buildClient()

            server
                .expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            shouldThrow<LlmProviderException> {
                client.generate("system", "pergunta")
            }
        }

        test("resposta sem bloco de texto lança LlmProviderException") {
            val (client, server) = buildClient()
            val responseJson = """{ "content": [] }"""

            server
                .expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

            shouldThrow<LlmProviderException> {
                client.generate("system", "pergunta")
            }
        }

        test("resposta truncada em max_tokens ainda devolve o texto (truncamento é logado, não é erro)") {
            val (client, server) = buildClient()
            val responseJson =
                """
                { "content": [ { "type": "text", "text": "Resposta cortada no meio" } ], "stop_reason": "max_tokens" }
                """.trimIndent()

            server
                .expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

            client.generate("system", "pergunta").text shouldBe "Resposta cortada no meio"
        }
    })

package com.eloiza.finrag.infrastructure.anthropic

import com.eloiza.finrag.domain.exception.LlmProviderException
import com.eloiza.finrag.domain.model.LlmResponse
import com.eloiza.finrag.domain.port.LlmClient
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class AnthropicLlmClient(
    restClientBuilder: RestClient.Builder,
    private val properties: AnthropicProperties,
) : LlmClient {
    private val log = LoggerFactory.getLogger(AnthropicLlmClient::class.java)
    private val restClient = restClientBuilder.baseUrl(properties.baseUrl).build()

    override fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): LlmResponse {
        try {
            val response =
                restClient
                    .post()
                    .uri("/v1/messages")
                    .header(API_KEY_HEADER, properties.apiKey)
                    .header(VERSION_HEADER, VERSION)
                    .body(
                        MessageRequest(
                            model = properties.model,
                            maxTokens = properties.maxTokens,
                            system = systemPrompt,
                            messages = listOf(MessageParam(role = "user", content = userPrompt)),
                        ),
                    ).retrieve()
                    .body<MessageResponse>()
                    ?: throw LlmProviderException("Resposta vazia da API de mensagens da Anthropic")

            val text = response.content.firstOrNull { it.type == "text" }?.text
            if (text.isNullOrBlank()) {
                throw LlmProviderException("Anthropic não retornou nenhum bloco de texto na resposta")
            }
            if (response.stopReason == STOP_REASON_MAX_TOKENS) {
                log.warn(
                    "Resposta da Anthropic truncada ao atingir max_tokens={} — considere aumentar finrag.anthropic.max-tokens",
                    properties.maxTokens,
                )
            }
            return LlmResponse(
                text = text,
                promptTokens = response.usage?.inputTokens ?: 0,
                completionTokens = response.usage?.outputTokens ?: 0,
            )
        } catch (e: RestClientException) {
            throw LlmProviderException("Falha ao chamar a API de mensagens da Anthropic", e)
        }
    }

    private companion object {
        const val API_KEY_HEADER = "x-api-key"
        const val VERSION_HEADER = "anthropic-version"
        const val VERSION = "2023-06-01"
        const val STOP_REASON_MAX_TOKENS = "max_tokens"
    }
}

private data class MessageRequest(
    val model: String,
    @JsonProperty("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<MessageParam>,
)

private data class MessageParam(
    val role: String,
    val content: String,
)

private data class MessageResponse(
    val content: List<ContentBlock>,
    @JsonProperty("stop_reason") val stopReason: String? = null,
    val usage: Usage? = null,
)

private data class ContentBlock(
    val type: String,
    val text: String? = null,
)

private data class Usage(
    @JsonProperty("input_tokens") val inputTokens: Int,
    @JsonProperty("output_tokens") val outputTokens: Int,
)

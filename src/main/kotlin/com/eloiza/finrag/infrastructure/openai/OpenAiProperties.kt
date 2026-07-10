package com.eloiza.finrag.infrastructure.openai

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "finrag.openai")
data class OpenAiProperties(
    val apiKey: String,
    val baseUrl: String = "https://api.openai.com",
    val embeddingModel: String = "text-embedding-3-small",
)

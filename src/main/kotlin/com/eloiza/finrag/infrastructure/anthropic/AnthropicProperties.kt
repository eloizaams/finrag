package com.eloiza.finrag.infrastructure.anthropic

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "finrag.anthropic")
data class AnthropicProperties(
    val apiKey: String,
    val baseUrl: String = "https://api.anthropic.com",
    val model: String = "claude-haiku-4-5",
    val maxTokens: Int = 1024,
)

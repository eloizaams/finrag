package com.eloiza.finrag.domain.port

interface LlmClient {
    fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): String
}

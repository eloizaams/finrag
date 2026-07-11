package com.eloiza.finrag.domain.port

import com.eloiza.finrag.domain.model.LlmResponse

interface LlmClient {
    fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): LlmResponse
}

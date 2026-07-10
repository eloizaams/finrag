package com.eloiza.finrag.domain.port

interface PipelineMetrics {
    fun <T> recordDuration(
        pipeline: String,
        stage: String,
        block: () -> T,
    ): T

    fun recordTokens(
        promptTokens: Int,
        completionTokens: Int,
    )
}

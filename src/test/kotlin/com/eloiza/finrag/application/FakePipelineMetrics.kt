package com.eloiza.finrag.application

import com.eloiza.finrag.domain.port.PipelineMetrics

class FakePipelineMetrics : PipelineMetrics {
    val recordedStages = mutableListOf<Pair<String, String>>()
    var recordedTokens: Pair<Int, Int>? = null
        private set

    override fun <T> recordDuration(
        pipeline: String,
        stage: String,
        block: () -> T,
    ): T {
        recordedStages.add(pipeline to stage)
        return block()
    }

    override fun recordTokens(
        promptTokens: Int,
        completionTokens: Int,
    ) {
        recordedTokens = promptTokens to completionTokens
    }
}

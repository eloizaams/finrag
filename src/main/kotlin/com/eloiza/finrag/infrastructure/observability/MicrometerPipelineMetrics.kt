package com.eloiza.finrag.infrastructure.observability

import com.eloiza.finrag.domain.port.PipelineMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.function.Supplier

@Component
class MicrometerPipelineMetrics(
    private val meterRegistry: MeterRegistry,
) : PipelineMetrics {
    override fun <T> recordDuration(
        pipeline: String,
        stage: String,
        block: () -> T,
    ): T {
        val timer =
            Timer
                .builder(STAGE_DURATION_METRIC)
                .tag(TAG_PIPELINE, pipeline)
                .tag(TAG_STAGE, stage)
                .register(meterRegistry)
        // Timer.record registra a duração mesmo quando o bloco lança exceção;
        // o cast só remove a anotação @Nullable da API Java (T já pode ser nulo).
        @Suppress("UNCHECKED_CAST")
        return timer.record(Supplier { block() }) as T
    }

    override fun recordTokens(
        promptTokens: Int,
        completionTokens: Int,
    ) {
        meterRegistry.counter(TOKENS_METRIC, TAG_TOKEN_TYPE, TYPE_PROMPT).increment(promptTokens.toDouble())
        meterRegistry.counter(TOKENS_METRIC, TAG_TOKEN_TYPE, TYPE_COMPLETION).increment(completionTokens.toDouble())
    }

    private companion object {
        const val STAGE_DURATION_METRIC = "finrag.pipeline.stage.duration"
        const val TOKENS_METRIC = "finrag.llm.tokens"
        const val TAG_PIPELINE = "pipeline"
        const val TAG_STAGE = "stage"
        const val TAG_TOKEN_TYPE = "type"
        const val TYPE_PROMPT = "prompt"
        const val TYPE_COMPLETION = "completion"
    }
}

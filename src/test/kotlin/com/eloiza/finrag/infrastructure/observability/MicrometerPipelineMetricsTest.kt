package com.eloiza.finrag.infrastructure.observability

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

class MicrometerPipelineMetricsTest :
    FunSpec({
        test("recordDuration executa o bloco, devolve o resultado e registra o timer com as tags corretas") {
            val registry = SimpleMeterRegistry()
            val metrics = MicrometerPipelineMetrics(registry)

            val result = metrics.recordDuration("question", "embedding") { "resultado do bloco" }

            result shouldBe "resultado do bloco"
            val timer = registry.find("finrag.pipeline.stage.duration").tags("pipeline", "question", "stage", "embedding").timer()
            timer?.count() shouldBe 1
        }

        test("recordDuration propaga exceção lançada pelo bloco e ainda assim registra o timer") {
            val registry = SimpleMeterRegistry()
            val metrics = MicrometerPipelineMetrics(registry)

            runCatching {
                metrics.recordDuration<Unit>("question", "llm") { throw IllegalStateException("falha simulada") }
            }

            val timer = registry.find("finrag.pipeline.stage.duration").tags("pipeline", "question", "stage", "llm").timer()
            timer?.count() shouldBe 1
        }

        test("recordTokens incrementa os contadores de prompt e completion separadamente") {
            val registry = SimpleMeterRegistry()
            val metrics = MicrometerPipelineMetrics(registry)

            metrics.recordTokens(promptTokens = 120, completionTokens = 15)

            registry.find("finrag.llm.tokens").tags("type", "prompt").counter()?.count() shouldBe 120.0
            registry.find("finrag.llm.tokens").tags("type", "completion").counter()?.count() shouldBe 15.0
        }
    })

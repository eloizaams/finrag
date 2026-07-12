package com.eloiza.finrag.eval

import com.eloiza.finrag.PostgresTestContainer
import com.eloiza.finrag.application.IngestDocumentUseCase
import com.eloiza.finrag.domain.model.User
import com.eloiza.finrag.domain.port.ChunkSearchRepository
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.UserRepository
import com.eloiza.finrag.infrastructure.RagProperties
import io.kotest.core.Tag
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.file.shouldExist
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import java.io.File
import java.time.Instant
import java.util.UUID

object RagEval : Tag()

/**
 * Harness de avaliação de retrieval (M8). NÃO roda no `./gradlew build`: usa a
 * API real da OpenAI (embeddings do corpus + das perguntas) e é executado sob
 * demanda via `OPENAI_API_KEY=... ./gradlew ragEval`.
 *
 * Mede recall@k, MRR e refusalAccuracy do golden dataset sobre todas as
 * combinações do grid topK × minSimilarity — com uma única rodada de
 * embeddings, já que o corte por k/threshold é pós-processamento em memória
 * (`RetrievalMetrics`). Não falha por métrica baixa: é medição para
 * calibração, não asserção de regressão.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
class RagRetrievalEvalSpec(
    ingestDocument: IngestDocumentUseCase,
    embeddingProvider: EmbeddingProvider,
    chunkSearchRepository: ChunkSearchRepository,
    userRepository: UserRepository,
    ragProperties: RagProperties,
) : FunSpec({
        tags(RagEval)

        beforeSpec {
            val key = System.getenv("OPENAI_API_KEY").orEmpty()
            require(key.isNotBlank() && !key.startsWith("test-only")) {
                "a avaliação usa a API real da OpenAI — rode via OPENAI_API_KEY=... ./gradlew ragEval"
            }
        }

        test("mede o retrieval do golden dataset e gera o relatório do grid") {
            val corpus = GoldenDatasetLoader.loadCorpus()
            val cases = GoldenDatasetLoader.loadCases()

            val userId =
                userRepository
                    .save(
                        User(
                            id = UUID.randomUUID(),
                            email = "rag-eval+${UUID.randomUUID()}@example.com",
                            passwordHash = "not-a-real-login",
                            createdAt = Instant.now(),
                        ),
                    ).id
            corpus.forEach { (filename, text) ->
                ingestDocument.ingest(userId, filename, text.toByteArray())
            }

            // uma única chamada de embeddings para todas as perguntas
            val questionEmbeddings = embeddingProvider.embed(cases.map { it.question })
            val retrievalsByCaseId =
                cases.zip(questionEmbeddings).associate { (case, embedding) ->
                    case.id to
                        chunkSearchRepository
                            .findMostSimilar(userId, embedding, k = RAW_TOP_N)
                            .map { RetrievedChunk(it.filename, it.content, it.similarity) }
                }

            val production = GridCombination(ragProperties.topK, ragProperties.minSimilarity)
            val grid =
                (TOP_KS.flatMap { topK -> MIN_SIMILARITIES.map { GridCombination(topK, it) } } + production)
                    .distinct()
            val summaries = grid.map { RetrievalMetrics.evaluate(it, cases, retrievalsByCaseId) }

            val report = File(REPORT_PATH)
            report.parentFile.mkdirs()
            report.writeText(RagEvalReportWriter.markdownReport(summaries, production, cases, retrievalsByCaseId))
            println(RagEvalReportWriter.consoleSummary(summaries, production))

            report.shouldExist()
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance

        private val TOP_KS = listOf(3, 5, 8)
        private val MIN_SIMILARITIES = listOf(0.15, 0.25, 0.35, 0.45)

        // maior que qualquer topK do grid: o corte é feito em memória depois
        private const val RAW_TOP_N = 10
        private const val REPORT_PATH = "build/reports/rag-eval/report.md"
    }
}

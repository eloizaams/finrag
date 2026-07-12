package com.eloiza.finrag.eval

/**
 * Um chunk retornado pela busca semântica, já traduzido para o vocabulário da
 * avaliação (nome do documento em vez de ids de banco).
 */
data class RetrievedChunk(
    val document: String,
    val content: String,
    val similarity: Double,
)

/** Uma combinação do grid de calibração. */
data class GridCombination(
    val topK: Int,
    val minSimilarity: Double,
)

/**
 * Resultado de um caso sob uma combinação do grid.
 *
 * - Caso com resposta: [hit] = toda substring esperada apareceu em algum chunk
 *   retido (documento certo + substring, com espaços normalizados);
 *   [firstHitRank] = posição (1-based) do primeiro chunk relevante.
 * - Caso sem resposta: [hit] = nenhum chunk sobrou acima do threshold — a
 *   recusa era o comportamento correto; [firstHitRank] não se aplica.
 *
 * [retained] guarda o que a busca de fato reteve, para o relatório mostrar o
 * que veio no lugar quando o caso falha.
 */
data class CaseEvaluation(
    val caseId: String,
    val hasAnswer: Boolean,
    val hit: Boolean,
    val firstHitRank: Int?,
    val retained: List<RetrievedChunk>,
)

/**
 * Métricas agregadas de uma combinação do grid.
 *
 * - [recall]: fração dos casos COM resposta em que todas as substrings
 *   esperadas apareceram nos chunks retidos (recall@k sob o threshold).
 * - [mrr]: média de 1/[CaseEvaluation.firstHitRank] sobre os casos com
 *   resposta; um caso que falha contribui com 0 — coerente com o recall.
 * - [refusalAccuracy]: fração dos casos SEM resposta em que nada passou do
 *   threshold (recusa correta).
 */
data class CombinationSummary(
    val combination: GridCombination,
    val evaluations: List<CaseEvaluation>,
) {
    val recall: Double = evaluations.filter { it.hasAnswer }.meanOf { if (it.hit) 1.0 else 0.0 }
    val mrr: Double =
        evaluations.filter { it.hasAnswer }.meanOf {
            if (it.hit && it.firstHitRank != null) 1.0 / it.firstHitRank else 0.0
        }
    val refusalAccuracy: Double = evaluations.filterNot { it.hasAnswer }.meanOf { if (it.hit) 1.0 else 0.0 }
}

object RetrievalMetrics {
    /**
     * Avalia todos os casos sob uma combinação do grid. [retrievalsByCaseId]
     * é o top-N bruto da busca (N >= maior topK do grid), em ordem decrescente
     * de similaridade — o corte por topK e threshold é feito aqui, em memória,
     * sem nova chamada de embedding.
     */
    fun evaluate(
        combination: GridCombination,
        cases: List<GoldenCase>,
        retrievalsByCaseId: Map<String, List<RetrievedChunk>>,
    ): CombinationSummary {
        val evaluations =
            cases.map { case ->
                val retrieved =
                    requireNotNull(retrievalsByCaseId[case.id]) {
                        "sem resultado de busca para o caso '${case.id}'"
                    }
                evaluateCase(case, retrieved, combination)
            }
        return CombinationSummary(combination, evaluations)
    }

    fun evaluateCase(
        case: GoldenCase,
        retrieved: List<RetrievedChunk>,
        combination: GridCombination,
    ): CaseEvaluation {
        val retained =
            retrieved
                .take(combination.topK)
                .filter { it.similarity >= combination.minSimilarity }

        if (!case.hasAnswer) {
            return CaseEvaluation(
                caseId = case.id,
                hasAnswer = false,
                hit = retained.isEmpty(),
                firstHitRank = null,
                retained = retained,
            )
        }

        val ranksBySubstring =
            case.expectedSubstrings.map { substring ->
                firstMatchRank(retained, case.expectedDocument!!, substring)
            }
        val hit = ranksBySubstring.all { it != null }
        return CaseEvaluation(
            caseId = case.id,
            hasAnswer = true,
            hit = hit,
            firstHitRank = ranksBySubstring.filterNotNull().minOrNull(),
            retained = retained,
        )
    }

    /** Posição (1-based) do primeiro chunk retido que satisfaz documento + substring. */
    private fun firstMatchRank(
        retained: List<RetrievedChunk>,
        expectedDocument: String,
        expectedSubstring: String,
    ): Int? {
        val normalized = normalizeWhitespace(expectedSubstring)
        val index =
            retained.indexOfFirst { chunk ->
                chunk.document == expectedDocument && normalizeWhitespace(chunk.content).contains(normalized)
            }
        return if (index >= 0) index + 1 else null
    }
}

private inline fun <T> List<T>.meanOf(selector: (T) -> Double): Double = if (isEmpty()) 0.0 else sumOf(selector) / size

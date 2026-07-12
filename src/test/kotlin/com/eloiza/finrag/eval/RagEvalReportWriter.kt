package com.eloiza.finrag.eval

import java.time.Instant

/**
 * Renderiza o resultado da avaliação: um resumo curto para o console e um
 * relatório markdown completo (tabela do grid + detalhe por caso da
 * combinação em produção, com os chunks que vieram no lugar nos casos que
 * falharam — o insumo para depurar chunking, dataset ou threshold).
 */
object RagEvalReportWriter {
    fun consoleSummary(
        summaries: List<CombinationSummary>,
        production: GridCombination,
    ): String {
        val best = summaries.maxBy { it.recall + it.refusalAccuracy }
        val current = summaries.first { it.combination == production }
        return buildString {
            appendLine("=== RAG eval — ${current.evaluations.size} casos ===")
            appendLine("produção ${format(production)}: ${metrics(current)}")
            appendLine("melhor    ${format(best.combination)}: ${metrics(best)}")
            appendLine("relatório completo: build/reports/rag-eval/report.md")
        }
    }

    fun markdownReport(
        summaries: List<CombinationSummary>,
        production: GridCombination,
        cases: List<GoldenCase>,
        rawRetrievalsByCaseId: Map<String, List<RetrievedChunk>>,
    ): String =
        buildString {
            appendLine("# Avaliação de retrieval — golden dataset")
            appendLine()
            appendLine("Gerado em ${Instant.now()}. Casos: ${summaries.first().evaluations.size}.")
            appendLine("Combinação em produção: `${format(production)}` (linha destacada).")
            appendLine()
            appendLine("## Grid topK × minSimilarity")
            appendLine()
            appendLine("| topK | minSimilarity | recall | MRR | refusalAccuracy |")
            appendLine("|------|---------------|--------|-----|-----------------|")
            summaries
                .sortedWith(compareBy({ it.combination.topK }, { it.combination.minSimilarity }))
                .forEach { summary ->
                    val marker = if (summary.combination == production) " **(produção)**" else ""
                    appendLine(
                        "| ${summary.combination.topK}$marker | ${summary.combination.minSimilarity} | " +
                            "${percent(summary.recall)} | ${decimal(summary.mrr)} | ${percent(summary.refusalAccuracy)} |",
                    )
                }
            appendLine()

            val current = summaries.first { it.combination == production }
            appendLine("## Detalhe por caso — combinação em produção")
            appendLine()
            appendLine("A coluna de similaridade do 1º acerto alimenta a análise de threshold:")
            appendLine("compare a faixa dos acertos com a dos chunks irrelevantes dos casos sem")
            appendLine("resposta — se as faixas se sobrepõem, não existe corte que separe.")
            appendLine()
            appendLine("| caso | tipo | resultado | rank do 1º acerto | similaridade do 1º acerto |")
            appendLine("|------|------|-----------|-------------------|---------------------------|")
            current.evaluations.forEach { eval ->
                val type = if (eval.hasAnswer) "resposta" else "sem resposta"
                val outcome = if (eval.hit) "✅" else "❌"
                val similarity = eval.firstHitSimilarity?.let(::decimal) ?: "—"
                appendLine("| ${eval.caseId} | $type | $outcome | ${eval.firstHitRank ?: "—"} | $similarity |")
            }
            appendLine()

            val failures = current.evaluations.filterNot { it.hit }
            if (failures.isEmpty()) {
                appendLine("Nenhum caso falhou na combinação em produção.")
            } else {
                appendLine("## Casos que falharam — top-${rawRetrievalsByCaseId.values.first().size} bruto da busca")
                appendLine()
                appendLine("Sem corte de topK/threshold, para diagnóstico. Chunks que satisfariam o")
                appendLine("caso (documento + substring esperada) aparecem marcados com ⬅.")
                failures.forEach { failure ->
                    val case = cases.first { it.id == failure.caseId }
                    appendLine()
                    appendLine("### ${failure.caseId}")
                    rawRetrievalsByCaseId.getValue(failure.caseId).forEachIndexed { index, chunk ->
                        val excerpt = normalizeWhitespace(chunk.content).take(EXCERPT_CHARS)
                        val marker = if (matchesExpectation(case, chunk)) " ⬅ esperado" else ""
                        appendLine("${index + 1}. `${chunk.document}` (${decimal(chunk.similarity)}): $excerpt…$marker")
                    }
                }
            }
        }

    private fun matchesExpectation(
        case: GoldenCase,
        chunk: RetrievedChunk,
    ): Boolean {
        if (!case.hasAnswer || chunk.document != case.expectedDocument) return false
        val content = normalizeWhitespace(chunk.content)
        return case.expectedSubstrings.any { content.contains(normalizeWhitespace(it)) }
    }

    private fun format(combination: GridCombination) = "topK=${combination.topK}, minSimilarity=${combination.minSimilarity}"

    private fun metrics(summary: CombinationSummary) =
        "recall=${percent(summary.recall)}, mrr=${decimal(summary.mrr)}, refusal=${percent(summary.refusalAccuracy)}"

    private fun percent(value: Double) = "%.0f%%".format(value * 100)

    private fun decimal(value: Double) = "%.2f".format(value)

    private const val EXCERPT_CHARS = 100
}

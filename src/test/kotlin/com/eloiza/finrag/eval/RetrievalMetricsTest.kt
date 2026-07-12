package com.eloiza.finrag.eval

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class RetrievalMetricsTest :
    FunSpec({
        fun answeredCase(
            id: String = "caso",
            substrings: List<String> = listOf("valor esperado"),
            document: String = "doc-a.md",
        ) = GoldenCase(
            id = id,
            question = "pergunta qualquer?",
            hasAnswer = true,
            expectedDocument = document,
            expectedSubstrings = substrings,
            referenceAnswer = "resposta",
        )

        fun noAnswerCase(id: String = "sem-resposta") =
            GoldenCase(
                id = id,
                question = "pergunta sem resposta no corpus?",
                hasAnswer = false,
                expectedDocument = null,
                expectedSubstrings = emptyList(),
                referenceAnswer = "não há",
            )

        fun chunk(
            content: String,
            similarity: Double,
            document: String = "doc-a.md",
        ) = RetrievedChunk(document, content, similarity)

        val combination = GridCombination(topK = 3, minSimilarity = 0.25)

        test("acerto na primeira posição: hit com rank 1") {
            val result =
                RetrievalMetrics.evaluateCase(
                    answeredCase(),
                    listOf(chunk("aqui está o valor esperado do caso", 0.8)),
                    combination,
                )

            result.hit.shouldBeTrue()
            result.firstHitRank shouldBe 1
        }

        test("chunk certo fora do topK não conta como acerto") {
            val retrieved =
                listOf(
                    chunk("irrelevante 1", 0.9),
                    chunk("irrelevante 2", 0.8),
                    chunk("irrelevante 3", 0.7),
                    chunk("o valor esperado veio em quarto", 0.6),
                )

            val result = RetrievalMetrics.evaluateCase(answeredCase(), retrieved, combination)

            result.hit.shouldBeFalse()
            result.firstHitRank shouldBe null
        }

        test("chunk certo abaixo do threshold é descartado") {
            val result =
                RetrievalMetrics.evaluateCase(
                    answeredCase(),
                    listOf(chunk("o valor esperado com score baixo", 0.10)),
                    combination,
                )

            result.hit.shouldBeFalse()
            result.retained shouldBe emptyList()
        }

        test("substring no documento errado não conta, mesmo com texto igual") {
            val result =
                RetrievalMetrics.evaluateCase(
                    answeredCase(document = "doc-a.md"),
                    listOf(chunk("contém o valor esperado", 0.9, document = "doc-b.md")),
                    combination,
                )

            result.hit.shouldBeFalse()
        }

        test("caso multi-chunk exige todas as substrings; rank é o do primeiro acerto") {
            val case = answeredCase(substrings = listOf("primeira parte", "segunda parte"))
            val ambas =
                listOf(
                    chunk("irrelevante", 0.9),
                    chunk("texto com a primeira parte", 0.8),
                    chunk("texto com a segunda parte", 0.7),
                )
            val apenasUma = ambas.dropLast(1)

            val completo = RetrievalMetrics.evaluateCase(case, ambas, combination)
            val parcial = RetrievalMetrics.evaluateCase(case, apenasUma, combination)

            completo.hit.shouldBeTrue()
            completo.firstHitRank shouldBe 2
            parcial.hit.shouldBeFalse()
        }

        test("comparação normaliza quebras de linha do chunk e da substring") {
            val result =
                RetrievalMetrics.evaluateCase(
                    answeredCase(substrings = listOf("valor\nesperado")),
                    listOf(chunk("veio o valor \n esperado quebrado em linhas", 0.9)),
                    combination,
                )

            result.hit.shouldBeTrue()
        }

        test("caso sem resposta acerta quando nada passa do threshold e falha quando passa") {
            val recusa =
                RetrievalMetrics.evaluateCase(noAnswerCase(), listOf(chunk("qualquer coisa", 0.10)), combination)
            val vazamento =
                RetrievalMetrics.evaluateCase(noAnswerCase(), listOf(chunk("qualquer coisa", 0.60)), combination)

            recusa.hit.shouldBeTrue()
            vazamento.hit.shouldBeFalse()
        }

        test("agregação separa recall/MRR (casos com resposta) de refusalAccuracy (sem resposta)") {
            val cases =
                listOf(
                    answeredCase(id = "acerta-rank-1"),
                    answeredCase(id = "acerta-rank-2"),
                    answeredCase(id = "erra"),
                    noAnswerCase(id = "recusa-correta"),
                    noAnswerCase(id = "recusa-vazada"),
                )
            val retrievals =
                mapOf(
                    "acerta-rank-1" to listOf(chunk("o valor esperado", 0.9)),
                    "acerta-rank-2" to listOf(chunk("irrelevante", 0.9), chunk("o valor esperado", 0.8)),
                    "erra" to listOf(chunk("irrelevante", 0.9)),
                    "recusa-correta" to listOf(chunk("irrelevante", 0.10)),
                    "recusa-vazada" to listOf(chunk("irrelevante", 0.90)),
                )

            val summary = RetrievalMetrics.evaluate(combination, cases, retrievals)

            summary.recall shouldBe (2.0 / 3.0 plusOrMinus 1e-9)
            summary.mrr shouldBe ((1.0 + 0.5 + 0.0) / 3.0 plusOrMinus 1e-9)
            summary.refusalAccuracy shouldBe (0.5 plusOrMinus 1e-9)
        }

        test("falha claro quando um caso não tem resultado de busca") {
            shouldThrow<IllegalArgumentException> {
                RetrievalMetrics.evaluate(combination, listOf(answeredCase(id = "orfao")), emptyMap())
            }
        }
    })

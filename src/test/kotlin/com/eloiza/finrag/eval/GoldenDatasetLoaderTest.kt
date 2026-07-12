package com.eloiza.finrag.eval

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty

// Além de cobrir o loader, este teste roda no build normal (sem tag, sem API
// externa) e protege a consistência dataset <-> corpus: se alguém editar um
// documento do corpus e quebrar uma substring-âncora, o CI acusa aqui.
class GoldenDatasetLoaderTest :
    FunSpec({
        test("carrega o corpus com os três documentos") {
            val corpus = GoldenDatasetLoader.loadCorpus()

            corpus.keys shouldBe
                setOf("relatorio-anual-acme.md", "politica-investimentos.md", "faq-fundos.md")
            corpus.values.forEach { it.shouldNotBeEmpty() }
        }

        test("carrega o dataset validado, dentro da faixa de 15-25 casos do requirements") {
            val cases = GoldenDatasetLoader.loadCases()

            cases.size shouldBeGreaterThanOrEqual 15
            (cases.size <= 25).shouldBeTrue()
        }

        test("composição mínima exigida pelo requirements: multi-chunk, sem-resposta e ambíguos") {
            val cases = GoldenDatasetLoader.loadCases()

            cases.count { !it.hasAnswer } shouldBeGreaterThanOrEqual 2
            cases.count { it.hasAnswer && it.expectedSubstrings.size >= 2 } shouldBeGreaterThanOrEqual 1
            cases.count { it.notes?.contains("Ambíguo") == true } shouldBeGreaterThanOrEqual 2
        }

        test("casos com resposta apontam substrings presentes e únicas no corpus") {
            val corpus = GoldenDatasetLoader.loadCorpus().mapValues { (_, text) -> normalizeWhitespace(text) }
            val cases = GoldenDatasetLoader.loadCases()

            cases.filter { it.hasAnswer }.forEach { case ->
                case.expectedSubstrings.forEach { substring ->
                    val normalized = normalizeWhitespace(substring)
                    corpus.getValue(case.expectedDocument!!).contains(normalized).shouldBeTrue()
                    corpus.count { (_, text) -> text.contains(normalized) } shouldBe 1
                }
            }
        }

        test("normalizeWhitespace colapsa quebras de linha e espaços repetidos") {
            normalizeWhitespace("  R\$ 320   milhões\nem\r\n dividendos ") shouldBe
                "R\$ 320 milhões em dividendos"
        }
    })

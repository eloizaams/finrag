package com.eloiza.finrag.domain.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class TextChunkerTest :
    FunSpec({
        test("texto com um único parágrafo curto vira um único chunk, sem alteração") {
            val chunker = TextChunker(maxChars = 50, overlapChars = 5)

            val chunks = chunker.chunk("chunk pequeno o suficiente")

            chunks shouldBe listOf("chunk pequeno o suficiente")
        }

        test("parágrafos consecutivos curtos são agregados no mesmo chunk") {
            val chunker = TextChunker(maxChars = 50, overlapChars = 5)

            val chunks = chunker.chunk("Primeiro paragrafo.\n\nSegundo paragrafo.")

            chunks shouldBe listOf("Primeiro paragrafo.\n\nSegundo paragrafo.")
        }

        test("parágrafo maior que max-chars é fatiado em janelas de max-chars") {
            val chunker = TextChunker(maxChars = 10, overlapChars = 0)

            val chunks = chunker.chunk("abcdefghijklmno")

            chunks shouldBe listOf("abcdefghij", "klmno")
        }

        test("chunk seguinte começa com o final (overlap) do chunk anterior, separado por espaço") {
            val chunker = TextChunker(maxChars = 10, overlapChars = 3)

            val chunks = chunker.chunk("1234567890\n\nabcde")

            chunks shouldBe listOf("1234567890", "890 abcde")
        }

        test("overlap não funde a última palavra de um chunk com a primeira do próximo") {
            val chunker = TextChunker(maxChars = 70, overlapChars = 10)

            val chunks =
                chunker.chunk(
                    "Paragrafo A com bastante conteudo para ocupar quase todo o limite.\n\nParagrafo B continua aqui.",
                )

            chunks[1] shouldBe " o limite. Paragrafo B continua aqui."
        }

        test("texto vazio ou só espaços/quebras de linha retorna lista vazia") {
            val chunker = TextChunker()

            chunker.chunk("").shouldBeEmpty()
            chunker.chunk("   \n\n   \n").shouldBeEmpty()
        }
    })

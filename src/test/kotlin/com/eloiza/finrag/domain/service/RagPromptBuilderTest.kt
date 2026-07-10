package com.eloiza.finrag.domain.service

import com.eloiza.finrag.domain.model.ScoredChunk
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.UUID

class RagPromptBuilderTest :
    FunSpec({
        val builder = RagPromptBuilder()

        fun scoredChunk(
            filename: String,
            content: String,
            similarity: Double = 0.9,
        ) = ScoredChunk(
            chunkId = UUID.randomUUID(),
            documentId = UUID.randomUUID(),
            filename = filename,
            content = content,
            similarity = similarity,
        )

        test("um único chunk é numerado [1] e citado com o filename de origem") {
            val chunk = scoredChunk("relatorio-q3.pdf", "A receita no terceiro trimestre foi de R\$ 10 milhões.")

            val prompt = builder.build("Qual foi a receita no Q3?", listOf(chunk))

            prompt.user shouldBe
                """
                Contexto:

                [1] (arquivo: relatorio-q3.pdf)
                A receita no terceiro trimestre foi de R${'$'} 10 milhões.

                Pergunta: Qual foi a receita no Q3?
                """.trimIndent()
        }

        test("múltiplos chunks são numerados em ordem, cada um com seu próprio filename") {
            val chunks =
                listOf(
                    scoredChunk("relatorio-q3.pdf", "Receita do Q3: R\$ 10 milhões."),
                    scoredChunk("relatorio-q4.pdf", "Receita do Q4: R\$ 12 milhões."),
                )

            val prompt = builder.build("Como a receita evoluiu?", chunks)

            prompt.user shouldBe
                """
                Contexto:

                [1] (arquivo: relatorio-q3.pdf)
                Receita do Q3: R${'$'} 10 milhões.

                [2] (arquivo: relatorio-q4.pdf)
                Receita do Q4: R${'$'} 12 milhões.

                Pergunta: Como a receita evoluiu?
                """.trimIndent()
        }

        test("system prompt instrui a responder só com base no contexto e a admitir quando não sabe") {
            val prompt = builder.build("Qualquer pergunta", listOf(scoredChunk("a.pdf", "conteúdo")))

            prompt.system shouldContain "Responda somente com informações presentes no contexto"
            prompt.system shouldContain "não invente uma resposta"
        }

        test("lista de chunks vazia lança exceção — quem decide 'sem contexto' é o use case, não o builder") {
            shouldThrow<IllegalArgumentException> {
                builder.build("Qualquer pergunta", emptyList())
            }
        }

        test("pergunta em branco lança exceção") {
            shouldThrow<IllegalArgumentException> {
                builder.build("   ", listOf(scoredChunk("a.pdf", "conteúdo")))
            }
        }
    })

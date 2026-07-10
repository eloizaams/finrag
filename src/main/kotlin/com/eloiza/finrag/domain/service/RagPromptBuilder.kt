package com.eloiza.finrag.domain.service

import com.eloiza.finrag.domain.model.RagPrompt
import com.eloiza.finrag.domain.model.ScoredChunk

class RagPromptBuilder {
    fun build(
        question: String,
        chunks: List<ScoredChunk>,
    ): RagPrompt {
        require(question.isNotBlank()) { "question não pode ser vazia" }
        require(chunks.isNotEmpty()) { "chunks não pode ser vazio" }

        val context =
            chunks
                .mapIndexed { index, chunk -> "[${index + 1}] (arquivo: ${chunk.filename})\n${chunk.content}" }
                .joinToString("\n\n")

        val user = "Contexto:\n\n$context\n\nPergunta: $question"

        return RagPrompt(system = SYSTEM_PROMPT, user = user)
    }

    private companion object {
        val SYSTEM_PROMPT =
            """
            Você é um assistente que responde perguntas sobre documentos financeiros com base apenas no contexto fornecido a seguir.

            Regras:
            - Responda somente com informações presentes no contexto. Não use conhecimento externo.
            - Se o contexto não tiver informação suficiente para responder à pergunta, diga claramente que os documentos indexados não contêm essa informação — não invente uma resposta.
            - Quando usar uma informação do contexto, cite o arquivo de origem entre colchetes, por exemplo: [relatorio-q3.pdf].
            """.trimIndent()
    }
}

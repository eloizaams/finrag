package com.eloiza.finrag.api

import com.eloiza.finrag.api.dto.AnswerResponse
import com.eloiza.finrag.api.dto.QuestionRequest
import com.eloiza.finrag.application.AskQuestionUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/questions")
@Tag(name = "Perguntas", description = "Q&A (RAG) sobre os documentos indexados do usuário autenticado")
class QuestionController(
    private val askQuestionUseCase: AskQuestionUseCase,
) {
    @Operation(
        summary = "Faz uma pergunta sobre os documentos indexados",
        description =
            "A pergunta vira embedding, os chunks mais similares do próprio usuário são buscados " +
                "no pgvector e a resposta é gerada pelo LLM citando as fontes. Sem contexto " +
                "relevante, retorna 200 com resposta padrão e sources vazio — não é erro.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Resposta gerada (sources pode vir vazio)"),
        ApiResponse(responseCode = "400", description = "Pergunta vazia ou acima do tamanho máximo (ProblemDetail)"),
        ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        ApiResponse(responseCode = "429", description = "Limite de perguntas por minuto excedido — ver header Retry-After"),
        ApiResponse(responseCode = "502", description = "Falha no provedor de embeddings ou de LLM (ProblemDetail)"),
    )
    @PostMapping
    fun ask(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: QuestionRequest,
    ): AnswerResponse = AnswerResponse.from(askQuestionUseCase.ask(userId, request.question))
}

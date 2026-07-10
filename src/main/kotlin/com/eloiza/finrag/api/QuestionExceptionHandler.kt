package com.eloiza.finrag.api

import com.eloiza.finrag.domain.exception.BlankQuestionException
import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.exception.LlmProviderException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [QuestionController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class QuestionExceptionHandler {
    private val log = LoggerFactory.getLogger(QuestionExceptionHandler::class.java)

    @ExceptionHandler(BlankQuestionException::class)
    fun handleBlankQuestion(ex: BlankQuestionException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Pergunta não pode ser vazia")

    @ExceptionHandler(EmbeddingProviderException::class)
    fun handleEmbeddingProvider(ex: EmbeddingProviderException): ProblemDetail {
        log.error("Falha ao gerar embedding da pergunta via provedor externo: {}", ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha ao gerar embedding da pergunta")
    }

    @ExceptionHandler(LlmProviderException::class)
    fun handleLlmProvider(ex: LlmProviderException): ProblemDetail {
        log.error("Falha ao gerar resposta via provedor de LLM: {}", ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha ao gerar resposta")
    }
}

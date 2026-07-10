package com.eloiza.finrag.api

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.exception.LlmProviderException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ProviderExceptionHandler {
    private val log = LoggerFactory.getLogger(ProviderExceptionHandler::class.java)

    @ExceptionHandler(EmbeddingProviderException::class)
    fun handleEmbeddingProvider(ex: EmbeddingProviderException): ProblemDetail {
        log.error("Falha ao gerar embeddings via provedor externo: {}", ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha ao gerar embeddings")
    }

    @ExceptionHandler(LlmProviderException::class)
    fun handleLlmProvider(ex: LlmProviderException): ProblemDetail {
        log.error("Falha ao gerar resposta via provedor de LLM: {}", ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha ao gerar resposta")
    }
}

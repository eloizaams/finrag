package com.eloiza.finrag.api

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.exception.LlmProviderException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ProviderExceptionHandler(
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(ProviderExceptionHandler::class.java)

    @ExceptionHandler(EmbeddingProviderException::class)
    fun handleEmbeddingProvider(ex: EmbeddingProviderException): ProblemDetail {
        log.error("Falha ao gerar embeddings via provedor externo: {}", ex.message, ex)
        recordProviderError(ex.provider, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha ao gerar embeddings")
    }

    @ExceptionHandler(LlmProviderException::class)
    fun handleLlmProvider(ex: LlmProviderException): ProblemDetail {
        log.error("Falha ao gerar resposta via provedor de LLM: {}", ex.message, ex)
        recordProviderError(ex.provider, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha ao gerar resposta")
    }

    private fun recordProviderError(
        provider: String,
        ex: Exception,
    ) {
        // A causa distingue o tipo real da falha (timeout, erro HTTP, ...); a própria
        // exceção de domínio é sempre a mesma por provedor e serve só de fallback.
        val errorType = (ex.cause ?: ex)::class.simpleName ?: "Unknown"
        meterRegistry
            .counter(
                PROVIDER_ERRORS_METRIC,
                TAG_PROVIDER,
                provider,
                TAG_ERROR_TYPE,
                errorType,
            ).increment()
    }

    private companion object {
        const val PROVIDER_ERRORS_METRIC = "finrag.provider.errors"
        const val TAG_PROVIDER = "provider"
        const val TAG_ERROR_TYPE = "error_type"
    }
}

package com.eloiza.finrag.api

import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.exception.EmptyDocumentException
import com.eloiza.finrag.domain.exception.UnsupportedDocumentTypeException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

@RestControllerAdvice(assignableTypes = [DocumentController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class DocumentExceptionHandler {
    private val log = LoggerFactory.getLogger(DocumentExceptionHandler::class.java)

    @ExceptionHandler(UnsupportedDocumentTypeException::class)
    fun handleUnsupportedType(ex: UnsupportedDocumentTypeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.message ?: "Tipo de documento não suportado")

    @ExceptionHandler(EmptyDocumentException::class)
    fun handleEmptyDocument(ex: EmptyDocumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.message ?: "Documento vazio ou sem texto extraível")

    @ExceptionHandler(EmbeddingProviderException::class)
    fun handleEmbeddingProvider(ex: EmbeddingProviderException): ProblemDetail {
        log.error("Falha ao gerar embeddings via provedor externo: {}", ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha ao gerar embeddings")
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(ex: MaxUploadSizeExceededException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede o tamanho máximo permitido")
}

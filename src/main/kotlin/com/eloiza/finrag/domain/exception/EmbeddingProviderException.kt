package com.eloiza.finrag.domain.exception

class EmbeddingProviderException(
    message: String,
    cause: Throwable? = null,
    val provider: String,
) : RuntimeException(message, cause)

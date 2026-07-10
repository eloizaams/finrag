package com.eloiza.finrag.domain.exception

class EmbeddingProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

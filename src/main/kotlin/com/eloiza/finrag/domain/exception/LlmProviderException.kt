package com.eloiza.finrag.domain.exception

class LlmProviderException(
    message: String,
    cause: Throwable? = null,
    val provider: String,
) : RuntimeException(message, cause)

package com.eloiza.finrag.domain.exception

class LlmProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

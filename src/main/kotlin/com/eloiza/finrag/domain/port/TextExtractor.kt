package com.eloiza.finrag.domain.port

import com.eloiza.finrag.domain.model.DocumentType

interface TextExtractor {
    fun extract(
        bytes: ByteArray,
        type: DocumentType,
    ): String
}

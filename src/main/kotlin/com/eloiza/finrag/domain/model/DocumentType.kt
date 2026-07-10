package com.eloiza.finrag.domain.model

import com.eloiza.finrag.domain.exception.UnsupportedDocumentTypeException

enum class DocumentType {
    PDF,
    MARKDOWN,
    ;

    companion object {
        fun fromFilename(filename: String): DocumentType {
            val extension = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return when (extension) {
                "pdf" -> PDF
                "md", "markdown" -> MARKDOWN
                else -> throw UnsupportedDocumentTypeException(extension)
            }
        }
    }
}

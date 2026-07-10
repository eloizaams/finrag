package com.eloiza.finrag.infrastructure.parsing

import com.eloiza.finrag.domain.model.DocumentType
import com.eloiza.finrag.domain.port.TextExtractor
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

@Component
class DefaultTextExtractor : TextExtractor {
    override fun extract(
        bytes: ByteArray,
        type: DocumentType,
    ): String =
        when (type) {
            DocumentType.PDF -> extractPdf(bytes)
            DocumentType.MARKDOWN -> String(bytes, Charsets.UTF_8)
        }

    private fun extractPdf(bytes: ByteArray): String = Loader.loadPDF(bytes).use { document -> PDFTextStripper().getText(document) }
}

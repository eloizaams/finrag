package com.eloiza.finrag

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
import java.io.ByteArrayOutputStream

fun renderPdf(text: String): ByteArray {
    PDDocument().use { document ->
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { contentStream ->
            contentStream.beginText()
            contentStream.setFont(PDType1Font(FontName.HELVETICA), 12f)
            contentStream.newLineAtOffset(50f, 700f)
            contentStream.showText(text)
            contentStream.endText()
        }

        val output = ByteArrayOutputStream()
        document.save(output)
        return output.toByteArray()
    }
}

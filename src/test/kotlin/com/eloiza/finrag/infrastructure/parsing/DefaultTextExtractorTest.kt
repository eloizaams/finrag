package com.eloiza.finrag.infrastructure.parsing

import com.eloiza.finrag.domain.model.DocumentType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
import java.io.ByteArrayOutputStream

class DefaultTextExtractorTest :
    FunSpec({
        val extractor = DefaultTextExtractor()

        test("extrai o texto de um PDF, com uma linha de conteúdo conhecida") {
            val pdfBytes = renderPdf("Receita liquida de R$ 100 milhoes no trimestre")

            val text = extractor.extract(pdfBytes, DocumentType.PDF)

            text shouldContain "Receita liquida de R$ 100 milhoes no trimestre"
        }

        test("extrai markdown como texto puro, preservando a marcação") {
            val markdown = "# Relatorio\n\nReceita: **R$ 100 milhoes**"

            val text = extractor.extract(markdown.toByteArray(), DocumentType.MARKDOWN)

            text shouldBe markdown
        }
    })

private fun renderPdf(text: String): ByteArray {
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

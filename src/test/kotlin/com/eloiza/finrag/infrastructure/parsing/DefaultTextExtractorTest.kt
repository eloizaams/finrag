package com.eloiza.finrag.infrastructure.parsing

import com.eloiza.finrag.domain.exception.EmptyDocumentException
import com.eloiza.finrag.domain.model.DocumentType
import com.eloiza.finrag.renderPdf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

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

        test("PDF corrompido lança EmptyDocumentException em vez de propagar a IOException do PDFBox") {
            val corruptBytes = "isso não é um PDF de verdade".toByteArray()

            shouldThrow<EmptyDocumentException> {
                extractor.extract(corruptBytes, DocumentType.PDF)
            }
        }
    })

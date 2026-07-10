package com.eloiza.finrag.domain.service

class TextChunker(
    private val maxChars: Int = 1000,
    private val overlapChars: Int = 200,
) {
    init {
        require(maxChars > 0) { "maxChars precisa ser positivo" }
        require(overlapChars in 0 until maxChars) { "overlapChars precisa ser menor que maxChars" }
    }

    fun chunk(text: String): List<String> {
        val paragraphs = splitParagraphs(text)
        if (paragraphs.isEmpty()) return emptyList()

        val baseChunks = buildBaseChunks(paragraphs)
        return applyOverlap(baseChunks)
    }

    private fun splitParagraphs(text: String): List<String> =
        text
            .replace("\r\n", "\n")
            .split(PARAGRAPH_BOUNDARY)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun buildBaseChunks(paragraphs: List<String>): List<String> {
        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()

        fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                chunks.add(buffer.toString())
                buffer.clear()
            }
        }

        for (paragraph in paragraphs) {
            if (paragraph.length > maxChars) {
                flushBuffer()
                chunks.addAll(paragraph.chunked(maxChars))
                continue
            }

            val wouldOverflow =
                buffer.isNotEmpty() && buffer.length + PARAGRAPH_SEPARATOR.length + paragraph.length > maxChars
            if (wouldOverflow) {
                flushBuffer()
            }
            if (buffer.isNotEmpty()) {
                buffer.append(PARAGRAPH_SEPARATOR)
            }
            buffer.append(paragraph)
        }
        flushBuffer()

        return chunks
    }

    private fun applyOverlap(baseChunks: List<String>): List<String> =
        baseChunks.mapIndexed { index, chunkText ->
            if (index == 0) {
                chunkText
            } else {
                baseChunks[index - 1].takeLast(overlapChars) + chunkText
            }
        }

    private companion object {
        val PARAGRAPH_BOUNDARY = Regex("\n\\s*\n+")
        const val PARAGRAPH_SEPARATOR = "\n\n"
    }
}

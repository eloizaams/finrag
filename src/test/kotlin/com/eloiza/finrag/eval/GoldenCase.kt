package com.eloiza.finrag.eval

/**
 * Um caso do golden dataset (`resources/golden/golden-dataset.json`).
 *
 * Quando [hasAnswer] é true, o acerto de retrieval exige que o chunk retornado
 * pertença a [expectedDocument] E contenha alguma das [expectedSubstrings]
 * (comparação com espaços em branco normalizados — a substring pode cruzar uma
 * quebra de linha do Markdown original).
 *
 * Quando [hasAnswer] é false, o comportamento correto é a recusa: nenhum chunk
 * deve superar o threshold de similaridade.
 */
data class GoldenCase(
    val id: String,
    val question: String,
    val hasAnswer: Boolean,
    val expectedDocument: String?,
    val expectedSubstrings: List<String>,
    val referenceAnswer: String,
    val notes: String? = null,
)

/**
 * Normaliza espaços em branco para comparação de substrings: colapsa qualquer
 * sequência de espaços/quebras de linha em um espaço simples.
 */
fun normalizeWhitespace(text: String): String = text.trim().replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")

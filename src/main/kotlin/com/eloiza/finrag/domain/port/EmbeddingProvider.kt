package com.eloiza.finrag.domain.port

interface EmbeddingProvider {
    fun embed(texts: List<String>): List<List<Float>>
}

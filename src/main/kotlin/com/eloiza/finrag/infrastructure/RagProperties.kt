package com.eloiza.finrag.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "finrag.rag")
data class RagProperties(
    val topK: Int = 5,
    val minSimilarity: Double = 0.25,
)

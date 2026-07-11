package com.eloiza.finrag.infrastructure.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "finrag.rate-limit")
data class RateLimitProperties(
    val questions: Rule = Rule(capacity = 10, periodSeconds = 60),
    val documents: Rule = Rule(capacity = 5, periodSeconds = 60),
) {
    data class Rule(
        val capacity: Long,
        val periodSeconds: Long,
    )
}

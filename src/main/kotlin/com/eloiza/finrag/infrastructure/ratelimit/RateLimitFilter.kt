package com.eloiza.finrag.infrastructure.ratelimit

import io.github.bucket4j.Bucket
import io.github.bucket4j.TimeMeter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter(
    private val properties: RateLimitProperties,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    private val timeMeter: TimeMeter = TimeMeter.SYSTEM_MILLISECONDS,
) : OncePerRequestFilter() {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val rule = ruleFor(request)
        // Sem regra para a rota, ou sem usuário autenticado (o Security responde 401 adiante)
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
        if (rule == null || userId == null) {
            filterChain.doFilter(request, response)
            return
        }

        val bucket = buckets.computeIfAbsent("$userId:${request.requestURI}") { newBucket(rule) }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            filterChain.doFilter(request, response)
            return
        }

        val retryAfterSeconds = nanosToCeilSeconds(probe.nanosToWaitForRefill)
        meterRegistry.counter(REJECTIONS_METRIC, "endpoint", request.requestURI).increment()
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Limite de requisições excedido para ${request.requestURI}; tente novamente em ${retryAfterSeconds}s",
            )
        objectMapper.writeValue(response.writer, problem)
    }

    private fun ruleFor(request: HttpServletRequest): RateLimitProperties.Rule? {
        if (request.method != "POST") return null
        return when (request.requestURI) {
            "/questions" -> properties.questions
            "/documents" -> properties.documents
            else -> null
        }
    }

    private fun newBucket(rule: RateLimitProperties.Rule): Bucket =
        Bucket
            .builder()
            .withCustomTimePrecision(timeMeter)
            .addLimit { it.capacity(rule.capacity).refillGreedy(rule.capacity, Duration.ofSeconds(rule.periodSeconds)) }
            .build()

    private fun nanosToCeilSeconds(nanos: Long): Long = (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND

    companion object {
        const val REJECTIONS_METRIC = "finrag.ratelimit.rejections"
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }
}

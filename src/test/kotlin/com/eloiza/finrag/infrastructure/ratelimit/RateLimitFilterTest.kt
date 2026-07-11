package com.eloiza.finrag.infrastructure.ratelimit

import io.github.bucket4j.TimeMeter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

private class MutableTimeMeter : TimeMeter {
    var nanos: Long = 0L

    override fun currentTimeNanos(): Long = nanos

    override fun isWallClockBased(): Boolean = true

    fun advanceSeconds(seconds: Long) {
        nanos += seconds * 1_000_000_000L
    }
}

class RateLimitFilterTest :
    FunSpec({
        lateinit var timeMeter: MutableTimeMeter
        lateinit var meterRegistry: SimpleMeterRegistry
        lateinit var filter: RateLimitFilter

        beforeTest {
            timeMeter = MutableTimeMeter()
            meterRegistry = SimpleMeterRegistry()
            filter =
                RateLimitFilter(
                    properties =
                        RateLimitProperties(
                            questions = RateLimitProperties.Rule(capacity = 2, periodSeconds = 60),
                            documents = RateLimitProperties.Rule(capacity = 1, periodSeconds = 60),
                        ),
                    meterRegistry = meterRegistry,
                    objectMapper = JsonMapper.builder().build(),
                    timeMeter = timeMeter,
                )
        }

        afterTest { SecurityContextHolder.clearContext() }

        fun perform(
            userId: UUID?,
            method: String = "POST",
            uri: String = "/questions",
        ): MockHttpServletResponse {
            SecurityContextHolder.clearContext()
            userId?.let {
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(it, null, emptyList())
            }
            val response = MockHttpServletResponse()
            filter.doFilter(MockHttpServletRequest(method, uri), response, MockFilterChain())
            return response
        }

        test("consome até a capacidade e responde 429 com Retry-After ao estourar") {
            val userId = UUID.randomUUID()

            repeat(2) { perform(userId).status shouldBe 200 }
            val blocked = perform(userId)

            blocked.status shouldBe 429
            blocked.getHeader("Retry-After") shouldNotBe null
            blocked.getHeader("Retry-After")!!.toInt() shouldBeGreaterThanOrEqual 1
            blocked.contentType!! shouldContain "application/problem+json"
            blocked.contentAsString shouldContain "Limite de requisições excedido"
            meterRegistry
                .find(RateLimitFilter.REJECTIONS_METRIC)
                .tags("endpoint", "/questions")
                .counter()!!
                .count() shouldBe 1.0
        }

        test("refill devolve tokens com o avanço do relógio") {
            val userId = UUID.randomUUID()
            repeat(2) { perform(userId).status shouldBe 200 }
            perform(userId).status shouldBe 429

            timeMeter.advanceSeconds(60)

            perform(userId).status shouldBe 200
        }

        test("baldes isolados: estourar o limite de um usuário não afeta outro") {
            val userA = UUID.randomUUID()
            val userB = UUID.randomUUID()
            repeat(2) { perform(userA).status shouldBe 200 }
            perform(userA).status shouldBe 429

            perform(userB).status shouldBe 200
        }

        test("baldes isolados por rota: estourar /documents não bloqueia /questions") {
            val userId = UUID.randomUUID()
            perform(userId, uri = "/documents").status shouldBe 200
            perform(userId, uri = "/documents").status shouldBe 429

            perform(userId, uri = "/questions").status shouldBe 200
        }

        test("rotas sem regra não são limitadas") {
            val userId = UUID.randomUUID()

            repeat(10) { perform(userId, method = "GET", uri = "/documents").status shouldBe 200 }
            repeat(10) { perform(userId, uri = "/auth/login").status shouldBe 200 }
        }

        test("sem usuário autenticado o filtro não se aplica (Security responde 401 adiante)") {
            repeat(10) { perform(userId = null).status shouldBe 200 }
        }
    })

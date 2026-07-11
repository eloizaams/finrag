package com.eloiza.finrag.infrastructure.ratelimit

import com.eloiza.finrag.PostgresTestContainer
import com.eloiza.finrag.api.dto.LoginRequest
import com.eloiza.finrag.api.dto.LoginResponse
import com.eloiza.finrag.api.dto.QuestionRequest
import com.eloiza.finrag.api.dto.RegisterRequest
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.LlmResponse
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.LlmClient
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@ApplyExtension(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "finrag.rate-limit.questions.capacity=2",
        "finrag.rate-limit.questions.period-seconds=2",
        "finrag.rate-limit.documents.capacity=1",
        "finrag.rate-limit.documents.period-seconds=60",
    ],
)
@AutoConfigureTestRestTemplate
@Import(RateLimitIntegrationTest.FakeProvidersConfig::class)
class RateLimitIntegrationTest(
    restTemplate: TestRestTemplate,
    fakeEmbeddingProvider: CountingFakeEmbeddingProvider,
    meterRegistry: MeterRegistry,
) : FunSpec({

        fun registerAndLogin(): String {
            val email = "user-${UUID.randomUUID()}@email.com"
            restTemplate.postForEntity("/auth/register", RegisterRequest(email, "senha123"), String::class.java)
            val login = restTemplate.postForEntity("/auth/login", LoginRequest(email, "senha123"), LoginResponse::class.java)
            return login.body!!.accessToken
        }

        fun ask(
            token: String,
            question: String = "Qual foi a receita?",
        ): org.springframework.http.ResponseEntity<String> {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(token)
            return restTemplate.exchange("/questions", HttpMethod.POST, HttpEntity(QuestionRequest(question), headers), String::class.java)
        }

        fun upload(token: String): org.springframework.http.ResponseEntity<String> {
            val resource =
                object : ByteArrayResource("Receita liquida de R$ 100 milhoes.".toByteArray()) {
                    override fun getFilename() = "relatorio.md"
                }
            val body = LinkedMultiValueMap<String, Any>()
            body.add("file", resource)
            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA
            headers.setBearerAuth(token)
            return restTemplate.postForEntity("/documents", HttpEntity(body, headers), String::class.java)
        }

        test("exceder o limite de POST /questions responde 429 com Retry-After, sem chamar o provedor") {
            val token = registerAndLogin()
            repeat(2) { ask(token).statusCode shouldBe HttpStatus.OK }
            val callsBefore = fakeEmbeddingProvider.calls.get()

            val blocked = ask(token)

            blocked.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
            blocked.headers.getFirst("Retry-After") shouldNotBe null
            fakeEmbeddingProvider.calls.get() shouldBe callsBefore
            meterRegistry
                .find("finrag.ratelimit.rejections")
                .tags("endpoint", "/questions")
                .counter()!!
                .count() shouldBeGreaterThan 0.0
        }

        test("limite de um usuário não afeta outro") {
            val tokenA = registerAndLogin()
            val tokenB = registerAndLogin()
            repeat(2) { ask(tokenA).statusCode shouldBe HttpStatus.OK }
            ask(tokenA).statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS

            ask(tokenB).statusCode shouldBe HttpStatus.OK
        }

        test("após o período de refill o usuário volta a ser atendido") {
            val token = registerAndLogin()
            repeat(2) { ask(token).statusCode shouldBe HttpStatus.OK }
            ask(token).statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS

            eventually(10.seconds) {
                ask(token).statusCode shouldBe HttpStatus.OK
            }
        }

        test("exceder o limite de POST /documents responde 429") {
            val token = registerAndLogin()
            upload(token).statusCode shouldBe HttpStatus.CREATED

            upload(token).statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        test("pergunta acima do tamanho máximo responde 400 sem chamar o provedor") {
            val token = registerAndLogin()
            val callsBefore = fakeEmbeddingProvider.calls.get()

            val response = ask(token, question = "a".repeat(QuestionRequest.MAX_QUESTION_LENGTH + 1))

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            fakeEmbeddingProvider.calls.get() shouldBe callsBefore
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }

    @TestConfiguration
    class FakeProvidersConfig {
        @Bean
        @Primary
        fun embeddingProvider(): CountingFakeEmbeddingProvider = CountingFakeEmbeddingProvider()

        @Bean
        @Primary
        fun llmClient(): LlmClient =
            object : LlmClient {
                override fun generate(
                    systemPrompt: String,
                    userPrompt: String,
                ): LlmResponse = LlmResponse(text = "resposta fake", promptTokens = 1, completionTokens = 1)
            }
    }
}

class CountingFakeEmbeddingProvider : EmbeddingProvider {
    val calls = AtomicInteger(0)

    override fun embed(texts: List<String>): List<List<Float>> {
        calls.incrementAndGet()
        return texts.map { List(Chunk.EMBEDDING_DIMENSIONS) { 0.1f } }
    }
}

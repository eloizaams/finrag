package com.eloiza.finrag.api

import com.eloiza.finrag.PostgresTestContainer
import com.eloiza.finrag.api.dto.AnswerResponse
import com.eloiza.finrag.api.dto.LoginRequest
import com.eloiza.finrag.api.dto.LoginResponse
import com.eloiza.finrag.api.dto.QuestionRequest
import com.eloiza.finrag.api.dto.RegisterRequest
import com.eloiza.finrag.api.dto.RegisterResponse
import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.exception.LlmProviderException
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.LlmResponse
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.LlmClient
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
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

@ApplyExtension(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(QuestionControllerTest.FakeProvidersConfig::class)
class QuestionControllerTest(
    restTemplate: TestRestTemplate,
    fakeEmbeddingProvider: BagOfWordsFakeEmbeddingProvider,
    fakeLlmClient: ControllableFakeLlmClient,
) : FunSpec({

        fun uniqueEmail() = "user-${UUID.randomUUID()}@email.com"

        fun registerAndLogin(): Pair<UUID, String> {
            val email = uniqueEmail()
            val register =
                restTemplate.postForEntity("/auth/register", RegisterRequest(email, "senha123"), RegisterResponse::class.java)
            val login = restTemplate.postForEntity("/auth/login", LoginRequest(email, "senha123"), LoginResponse::class.java)
            return register.body!!.id to login.body!!.accessToken
        }

        fun uploadDocument(
            token: String,
            filename: String,
            content: String,
        ) {
            val resource =
                object : ByteArrayResource(content.toByteArray()) {
                    override fun getFilename() = filename
                }
            val body = LinkedMultiValueMap<String, Any>()
            body.add("file", resource)

            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA
            headers.setBearerAuth(token)

            val response = restTemplate.postForEntity("/documents", HttpEntity(body, headers), String::class.java)
            check(response.statusCode == HttpStatus.CREATED) { "falha ao preparar fixture: ${response.statusCode} ${response.body}" }
        }

        fun askRequest(
            token: String?,
            question: String,
        ): HttpEntity<QuestionRequest> {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            token?.let { headers.setBearerAuth(it) }
            return HttpEntity(QuestionRequest(question), headers)
        }

        fun ask(
            token: String?,
            question: String,
        ) = restTemplate.exchange("/questions", HttpMethod.POST, askRequest(token, question), AnswerResponse::class.java)

        fun askRaw(
            token: String?,
            question: String,
        ) = restTemplate.exchange("/questions", HttpMethod.POST, askRequest(token, question), String::class.java)

        afterTest {
            fakeEmbeddingProvider.shouldFail = false
            fakeLlmClient.shouldFail = false
        }

        test("pergunta com contexto encontrado retorna 200 com o chunk mais relevante como primeira fonte") {
            val (_, token) = registerAndLogin()
            uploadDocument(token, "receita.md", "A receita liquida da empresa cresceu no terceiro trimestre.")
            uploadDocument(token, "clima.md", "A previsao do tempo indica chuva forte amanha.")

            val response = ask(token, "Qual foi a receita no terceiro trimestre?")

            response.statusCode shouldBe HttpStatus.OK
            val body = response.body!!
            body.answer shouldBe "resposta gerada pelo LLM fake"
            body.sources.first().filename shouldBe "receita.md"
        }

        test("isolamento por usuário: fontes de outro usuário nunca aparecem, mesmo sendo mais similares") {
            val (_, tokenA) = registerAndLogin()
            val (_, tokenB) = registerAndLogin()
            uploadDocument(tokenA, "meu.md", "Receita do terceiro trimestre da minha empresa.")
            uploadDocument(tokenB, "alheio.md", "Receita liquida do terceiro trimestre da outra empresa.")

            val response = ask(tokenA, "Qual foi a receita no terceiro trimestre?")

            response.statusCode shouldBe HttpStatus.OK
            response.body!!.sources.map { it.filename } shouldBe listOf("meu.md")
        }

        test("usuário sem documentos indexados recebe 200 com resposta padrão e fontes vazias") {
            val (_, token) = registerAndLogin()

            val response = ask(token, "Qualquer pergunta sem contexto indexado")

            response.statusCode shouldBe HttpStatus.OK
            val body = response.body!!
            body.answer shouldBe "Os documentos indexados não contêm informação relevante para responder a essa pergunta."
            body.sources.shouldBeEmpty()
        }

        test("usuário só com documentos irrelevantes recebe 200 com resposta padrão e fontes vazias") {
            val (_, token) = registerAndLogin()
            uploadDocument(token, "clima.md", "A previsao do tempo indica chuva forte amanha.")

            val response = ask(token, "Qual foi a receita no terceiro trimestre?")

            response.statusCode shouldBe HttpStatus.OK
            val body = response.body!!
            body.answer shouldBe "Os documentos indexados não contêm informação relevante para responder a essa pergunta."
            body.sources.shouldBeEmpty()
        }

        test("pergunta em branco retorna 400") {
            val (_, token) = registerAndLogin()

            val response = askRaw(token, "   ")

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("falha do provedor de embeddings retorna 502") {
            val (_, token) = registerAndLogin()
            fakeEmbeddingProvider.shouldFail = true

            val response = askRaw(token, "Qualquer pergunta")

            response.statusCode shouldBe HttpStatus.BAD_GATEWAY
        }

        test("falha do provedor de LLM retorna 502") {
            val (_, token) = registerAndLogin()
            uploadDocument(token, "receita.md", "A receita liquida da empresa cresceu no terceiro trimestre.")
            fakeLlmClient.shouldFail = true

            val response = askRaw(token, "Qual foi a receita no terceiro trimestre?")

            response.statusCode shouldBe HttpStatus.BAD_GATEWAY
        }

        test("POST /questions sem token retorna 401") {
            askRaw(null, "Qualquer pergunta").statusCode shouldBe HttpStatus.UNAUTHORIZED
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
        fun embeddingProvider(): BagOfWordsFakeEmbeddingProvider = BagOfWordsFakeEmbeddingProvider()

        @Bean
        @Primary
        fun llmClient(): ControllableFakeLlmClient = ControllableFakeLlmClient()
    }
}

/**
 * Fake determinístico de embeddings baseado em bag-of-words: cada palavra do texto
 * incrementa uma dimensão fixa (hash da palavra). Ao contrário de um vetor uniforme,
 * isso preserva a propriedade que importa nos testes de busca semântica — textos que
 * compartilham vocabulário ficam mais próximos por similaridade de cosseno do que
 * textos sem nada em comum — sem depender da OpenAI de verdade.
 */
class BagOfWordsFakeEmbeddingProvider : EmbeddingProvider {
    @Volatile
    var shouldFail: Boolean = false

    override fun embed(texts: List<String>): List<List<Float>> {
        if (shouldFail) {
            throw EmbeddingProviderException("falha simulada do provedor de embeddings")
        }
        return texts.map { it.toBagOfWordsVector() }
    }

    private fun String.toBagOfWordsVector(): List<Float> {
        val vector = FloatArray(Chunk.EMBEDDING_DIMENSIONS)
        lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() }
            .forEach { word ->
                val index = Math.floorMod(word.hashCode(), Chunk.EMBEDDING_DIMENSIONS)
                vector[index] += 1f
            }
        return vector.toList()
    }
}

class ControllableFakeLlmClient : LlmClient {
    @Volatile
    var shouldFail: Boolean = false

    override fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): LlmResponse {
        if (shouldFail) {
            throw LlmProviderException("falha simulada do provedor de LLM")
        }
        return LlmResponse(text = "resposta gerada pelo LLM fake", promptTokens = 50, completionTokens = 10)
    }
}

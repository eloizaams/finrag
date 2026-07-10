package com.eloiza.finrag.api

import com.eloiza.finrag.PostgresTestContainer
import com.eloiza.finrag.api.dto.DocumentResponse
import com.eloiza.finrag.api.dto.LoginRequest
import com.eloiza.finrag.api.dto.LoginResponse
import com.eloiza.finrag.api.dto.RegisterRequest
import com.eloiza.finrag.api.dto.RegisterResponse
import com.eloiza.finrag.domain.exception.EmbeddingProviderException
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.infrastructure.persistence.JpaChunkRepository
import com.eloiza.finrag.infrastructure.persistence.JpaDocumentRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
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
import java.io.ByteArrayOutputStream
import java.util.UUID

@ApplyExtension(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(DocumentControllerTest.FakeEmbeddingProviderConfig::class)
class DocumentControllerTest(
    restTemplate: TestRestTemplate,
    jpaDocumentRepository: JpaDocumentRepository,
    jpaChunkRepository: JpaChunkRepository,
    fakeEmbeddingProvider: ControllableFakeEmbeddingProvider,
) : FunSpec({

        fun uniqueEmail() = "user-${UUID.randomUUID()}@email.com"

        fun registerAndLogin(): Pair<UUID, String> {
            val email = uniqueEmail()
            val register =
                restTemplate.postForEntity("/auth/register", RegisterRequest(email, "senha123"), RegisterResponse::class.java)
            val login = restTemplate.postForEntity("/auth/login", LoginRequest(email, "senha123"), LoginResponse::class.java)
            return register.body!!.id to login.body!!.accessToken
        }

        fun uploadRequest(
            token: String?,
            filename: String,
            bytes: ByteArray,
        ): HttpEntity<LinkedMultiValueMap<String, Any>> {
            val resource =
                object : ByteArrayResource(bytes) {
                    override fun getFilename() = filename
                }
            val body = LinkedMultiValueMap<String, Any>()
            body.add("file", resource)

            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA
            token?.let { headers.setBearerAuth(it) }

            return HttpEntity(body, headers)
        }

        fun upload(
            token: String?,
            filename: String,
            bytes: ByteArray,
        ) = restTemplate.postForEntity("/documents", uploadRequest(token, filename, bytes), DocumentResponse::class.java)

        fun uploadRaw(
            token: String?,
            filename: String,
            bytes: ByteArray,
        ) = restTemplate.postForEntity("/documents", uploadRequest(token, filename, bytes), String::class.java)

        fun listDocuments(token: String?): List<DocumentResponse> {
            val headers = HttpHeaders()
            token?.let { headers.setBearerAuth(it) }
            val response =
                restTemplate.exchange(
                    "/documents",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    Array<DocumentResponse>::class.java,
                )
            return response.body.orEmpty().toList()
        }

        afterTest { fakeEmbeddingProvider.shouldFail = false }

        test("ingestão de PDF com sucesso retorna 201 com chunks e embeddings persistidos batendo com o chunkCount") {
            val (_, token) = registerAndLogin()
            val pdfBytes = renderPdf("Receita liquida de R$ 100 milhoes no trimestre")

            val response = upload(token, "relatorio.pdf", pdfBytes)

            response.statusCode shouldBe HttpStatus.CREATED
            val body = response.body!!
            body.filename shouldBe "relatorio.pdf"

            val savedDocument = jpaDocumentRepository.findById(body.id).get()
            savedDocument.chunkCount shouldBe body.chunkCount

            val savedChunks = jpaChunkRepository.findAll().filter { it.documentId == body.id }
            savedChunks shouldHaveSize body.chunkCount
            savedChunks.forEach { it.embedding.size shouldBe Chunk.EMBEDDING_DIMENSIONS }
        }

        test("ingestão de Markdown com sucesso retorna 201") {
            val (_, token) = registerAndLogin()
            val markdown = "# Relatorio\n\nReceita liquida de R$ 100 milhoes no trimestre.".toByteArray()

            val response = upload(token, "relatorio.md", markdown)

            response.statusCode shouldBe HttpStatus.CREATED
            response.body?.filename shouldBe "relatorio.md"
        }

        test("upload de formato não suportado retorna 415") {
            val (_, token) = registerAndLogin()

            val response = uploadRaw(token, "imagem.png", "conteudo qualquer".toByteArray())

            response.statusCode shouldBe HttpStatus.UNSUPPORTED_MEDIA_TYPE
        }

        test("upload de arquivo vazio ou sem texto extraível retorna 422 e nada é persistido") {
            val (userId, token) = registerAndLogin()

            val response = uploadRaw(token, "vazio.md", "   \n\n   ".toByteArray())

            response.statusCode shouldBe HttpStatus.UNPROCESSABLE_CONTENT
            jpaDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(userId).shouldBeEmpty()
        }

        test("falha do provedor de embeddings retorna 502 e não persiste documento nem chunks") {
            val (userId, token) = registerAndLogin()
            fakeEmbeddingProvider.shouldFail = true

            val response = uploadRaw(token, "relatorio.md", "Receita liquida de R$ 100 milhoes.".toByteArray())

            response.statusCode shouldBe HttpStatus.BAD_GATEWAY
            jpaDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(userId).shouldBeEmpty()
        }

        test("GET /documents retorna só os documentos do usuário do token") {
            val (_, tokenA) = registerAndLogin()
            val (_, tokenB) = registerAndLogin()
            upload(tokenA, "documento-a.md", "Conteudo do usuario A".toByteArray())
            upload(tokenB, "documento-b.md", "Conteudo do usuario B".toByteArray())

            listDocuments(tokenA).map { it.filename } shouldBe listOf("documento-a.md")
            listDocuments(tokenB).map { it.filename } shouldBe listOf("documento-b.md")
        }

        test("POST e GET /documents sem token retornam 401") {
            uploadRaw(null, "relatorio.md", "conteudo".toByteArray()).statusCode shouldBe HttpStatus.UNAUTHORIZED

            val headers = HttpHeaders()
            val getResponse =
                restTemplate.exchange("/documents", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
            getResponse.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }

    @TestConfiguration
    class FakeEmbeddingProviderConfig {
        @Bean
        @Primary
        fun embeddingProvider(): ControllableFakeEmbeddingProvider = ControllableFakeEmbeddingProvider()
    }
}

class ControllableFakeEmbeddingProvider : EmbeddingProvider {
    @Volatile
    var shouldFail: Boolean = false

    override fun embed(texts: List<String>): List<List<Float>> {
        if (shouldFail) {
            throw EmbeddingProviderException("falha simulada do provedor de embeddings")
        }
        return texts.map { text -> List(Chunk.EMBEDDING_DIMENSIONS) { text.hashCode().toFloat() } }
    }
}

private fun renderPdf(text: String): ByteArray {
    PDDocument().use { document ->
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { contentStream ->
            contentStream.beginText()
            contentStream.setFont(PDType1Font(FontName.HELVETICA), 12f)
            contentStream.newLineAtOffset(50f, 700f)
            contentStream.showText(text)
            contentStream.endText()
        }

        val output = ByteArrayOutputStream()
        document.save(output)
        return output.toByteArray()
    }
}

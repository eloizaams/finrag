package com.eloiza.finrag.infrastructure.security

import com.eloiza.finrag.domain.model.User
import com.eloiza.finrag.domain.port.TokenProvider
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.Instant
import java.util.UUID

@ApplyExtension(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SecurityConfigTest(
    restTemplate: TestRestTemplate,
    tokenProvider: TokenProvider,
) : FunSpec({

        val protectedPath = URI.create("/uma-rota-protegida-qualquer")

        test("GET /actuator/health não exige autenticação") {
            val response = restTemplate.getForEntity("/actuator/health", String::class.java)

            response.statusCode shouldBe HttpStatus.OK
        }

        test("rota protegida sem token retorna 401") {
            val response = restTemplate.getForEntity(protectedPath, String::class.java)

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        test("rota protegida com token malformado retorna 401") {
            val request =
                RequestEntity.get(protectedPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")
                    .build()

            val response = restTemplate.exchange(request, String::class.java)

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        test("rota protegida com token válido passa da autenticação (404 de rota inexistente, não 401)") {
            val user =
                User(
                    id = UUID.randomUUID(),
                    email = "ana@email.com",
                    passwordHash = "hashed:senha123",
                    createdAt = Instant.now(),
                )
            val token = tokenProvider.generate(user).token

            val request =
                RequestEntity.get(protectedPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .build()

            val response = restTemplate.exchange(request, String::class.java)

            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).apply { start() }
    }
}

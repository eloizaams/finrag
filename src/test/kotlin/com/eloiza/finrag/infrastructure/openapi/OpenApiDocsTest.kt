package com.eloiza.finrag.infrastructure.openapi

import com.eloiza.finrag.PostgresTestContainer
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus

@ApplyExtension(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OpenApiDocsTest(
    restTemplate: TestRestTemplate,
) : FunSpec({

        test("GET /v3/api-docs sem token retorna 200 com todos os endpoints e o esquema bearerAuth") {
            val response = restTemplate.getForEntity("/v3/api-docs", Map::class.java)

            response.statusCode shouldBe HttpStatus.OK
            val body = response.body!!

            @Suppress("UNCHECKED_CAST")
            val paths = body["paths"] as Map<String, Any>
            listOf(
                "/auth/register",
                "/auth/login",
                "/documents",
                "/documents/{id}",
                "/questions",
            ).forEach { paths shouldContainKey it }

            @Suppress("UNCHECKED_CAST")
            val components = body["components"] as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val securitySchemes = components["securitySchemes"] as Map<String, Any>
            securitySchemes shouldContainKey "bearerAuth"
        }

        test("GET /swagger-ui.html sem token é acessível") {
            val response = restTemplate.getForEntity("/swagger-ui.html", String::class.java)

            (response.statusCode.is2xxSuccessful || response.statusCode.is3xxRedirection).shouldBeTrue()
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }
}

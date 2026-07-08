package com.eloiza.finrag.api

import com.eloiza.finrag.api.dto.LoginRequest
import com.eloiza.finrag.api.dto.LoginResponse
import com.eloiza.finrag.api.dto.RegisterRequest
import com.eloiza.finrag.api.dto.RegisterResponse
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import com.eloiza.finrag.PostgresTestContainer
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import java.util.UUID

@ApplyExtension(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthControllerTest(
    restTemplate: TestRestTemplate,
) : FunSpec({

        fun uniqueEmail() = "user-${UUID.randomUUID()}@email.com"

        test("registro com sucesso retorna 201 e corpo sem senha") {
            val email = uniqueEmail()

            val response =
                restTemplate.postForEntity("/auth/register", RegisterRequest(email, "senha123"), RegisterResponse::class.java)

            response.statusCode shouldBe HttpStatus.CREATED
            response.body?.email shouldBe email
        }

        test("registro com email já cadastrado retorna 409") {
            val email = uniqueEmail()
            restTemplate.postForEntity("/auth/register", RegisterRequest(email, "senha123"), RegisterResponse::class.java)

            val response =
                restTemplate.postForEntity("/auth/register", RegisterRequest(email, "outrasenha"), String::class.java)

            response.statusCode shouldBe HttpStatus.CONFLICT
        }

        test("registro com payload inválido (email malformado, senha curta) retorna 400") {
            val response =
                restTemplate.postForEntity("/auth/register", RegisterRequest("nao-e-email", "curta"), String::class.java)

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("login com sucesso retorna 200 com accessToken presente") {
            val email = uniqueEmail()
            restTemplate.postForEntity("/auth/register", RegisterRequest(email, "senha123"), RegisterResponse::class.java)

            val response =
                restTemplate.postForEntity("/auth/login", LoginRequest(email, "senha123"), LoginResponse::class.java)

            response.statusCode shouldBe HttpStatus.OK
            response.body?.accessToken.isNullOrBlank() shouldBe false
        }

        test("login com senha errada retorna 401 com mensagem genérica") {
            val email = uniqueEmail()
            restTemplate.postForEntity("/auth/register", RegisterRequest(email, "senha123"), RegisterResponse::class.java)

            val response = restTemplate.postForEntity("/auth/login", LoginRequest(email, "senhaerrada"), String::class.java)

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            response.body.orEmpty() shouldContain "Credenciais inválidas"
        }

        test("login com email inexistente retorna 401 com a mesma mensagem genérica") {
            val response =
                restTemplate.postForEntity(
                    "/auth/login",
                    LoginRequest(uniqueEmail(), "qualquersenha"),
                    String::class.java,
                )

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            response.body.orEmpty() shouldContain "Credenciais inválidas"
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }
}

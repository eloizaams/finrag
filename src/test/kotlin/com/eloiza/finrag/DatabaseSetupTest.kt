package com.eloiza.finrag

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import javax.sql.DataSource

@ApplyExtension(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class DatabaseSetupTest(
    dataSource: DataSource,
    restTemplate: TestRestTemplate,
) : FunSpec({

        test("extensão pgvector está habilitada no banco") {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    val resultSet = statement.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'")
                    resultSet.next() shouldBe true
                }
            }
        }

        test("GET /actuator/health responde 200 com status UP") {
            val response = restTemplate.getForEntity("/actuator/health", String::class.java)

            response.statusCode shouldBe HttpStatus.OK
            response.body.orEmpty() shouldContain "\"status\":\"UP\""
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }
}

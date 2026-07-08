package com.eloiza.finrag

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class FinragApplicationTests {

	@Test
	fun contextLoads() {
	}

	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
	}
}

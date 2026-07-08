package com.eloiza.finrag

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection

@SpringBootTest
class FinragApplicationTests {
    @Test
    fun contextLoads() {
    }

    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }
}

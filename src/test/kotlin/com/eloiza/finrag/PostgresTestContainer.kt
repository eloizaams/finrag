package com.eloiza.finrag

import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

// Nunca chamar stop(): o Ryuk (resource reaper do Testcontainers) derruba o
// container quando a JVM de teste encerra; parar aqui quebraria as outras classes.
object PostgresTestContainer {
    val instance: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).apply { start() }
    }
}

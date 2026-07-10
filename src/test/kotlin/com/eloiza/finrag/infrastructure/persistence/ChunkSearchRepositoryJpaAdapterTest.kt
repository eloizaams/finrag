package com.eloiza.finrag.infrastructure.persistence

import com.eloiza.finrag.PostgresTestContainer
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.model.User
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ApplyExtension(SpringExtension::class)
@SpringBootTest
class ChunkSearchRepositoryJpaAdapterTest(
    jpaUserRepository: JpaUserRepository,
    private val documentRepository: DocumentRepositoryJpaAdapter,
    private val adapter: ChunkSearchRepositoryJpaAdapter,
) : FunSpec({

        fun persistUser(): UUID {
            val user =
                User(
                    id = UUID.randomUUID(),
                    email = "${UUID.randomUUID()}@email.com",
                    passwordHash = "hashed:senha123",
                    createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                )
            return UserRepositoryJpaAdapter(jpaUserRepository).save(user).id
        }

        // vetores construídos à mão para ter uma similaridade de cosseno previsível
        // contra queryEmbedding: dimensão 0 é o "eixo" da pergunta.
        fun vectorWithComponents(vararg components: Pair<Int, Float>): List<Float> {
            val vector = MutableList(Chunk.EMBEDDING_DIMENSIONS) { 0f }
            components.forEach { (index, value) -> vector[index] = value }
            return vector
        }

        fun persistDocumentWithChunk(
            userId: UUID,
            filename: String,
            content: String,
            embedding: List<Float>,
        ): Chunk {
            val document =
                Document(
                    id = UUID.randomUUID(),
                    userId = userId,
                    filename = filename,
                    chunkCount = 1,
                    createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                )
            val chunk =
                Chunk(
                    id = UUID.randomUUID(),
                    documentId = document.id,
                    index = 0,
                    content = content,
                    embedding = embedding,
                )
            documentRepository.save(document, listOf(chunk))
            return chunk
        }

        val queryEmbedding = vectorWithComponents(0 to 1f)

        test("retorna os chunks ordenados por similaridade de cosseno decrescente") {
            val userId = persistUser()
            val closeChunk = persistDocumentWithChunk(userId, "perto.pdf", "conteúdo perto", vectorWithComponents(0 to 1f))
            val mediumChunk =
                persistDocumentWithChunk(
                    userId,
                    "medio.pdf",
                    "conteúdo médio",
                    vectorWithComponents(0 to 0.8f, 1 to 0.6f),
                )
            val farChunk = persistDocumentWithChunk(userId, "longe.pdf", "conteúdo longe", vectorWithComponents(1 to 1f))

            val result = adapter.findMostSimilar(userId, queryEmbedding, k = 3)

            result.map { it.chunkId } shouldContainExactly listOf(closeChunk.id, mediumChunk.id, farChunk.id)
        }

        test("k limita a quantidade de chunks retornados ao(s) mais similar(es)") {
            val userId = persistUser()
            val closeChunk = persistDocumentWithChunk(userId, "perto.pdf", "conteúdo perto", vectorWithComponents(0 to 1f))
            persistDocumentWithChunk(userId, "medio.pdf", "conteúdo médio", vectorWithComponents(0 to 0.8f, 1 to 0.6f))
            persistDocumentWithChunk(userId, "longe.pdf", "conteúdo longe", vectorWithComponents(1 to 1f))

            val result = adapter.findMostSimilar(userId, queryEmbedding, k = 1)

            result.map { it.chunkId } shouldContainExactly listOf(closeChunk.id)
        }

        test("chunks de outro usuário nunca entram no resultado, mesmo sendo mais similares") {
            val userId = persistUser()
            val otherUserId = persistUser()
            val ownChunk =
                persistDocumentWithChunk(
                    userId,
                    "meu.pdf",
                    "meu conteúdo",
                    vectorWithComponents(0 to 0.8f, 1 to 0.6f),
                )
            persistDocumentWithChunk(otherUserId, "alheio.pdf", "conteúdo de outro usuário", vectorWithComponents(0 to 1f))

            val result = adapter.findMostSimilar(userId, queryEmbedding, k = 5)

            result.map { it.chunkId } shouldContainExactly listOf(ownChunk.id)
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }
}

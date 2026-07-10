package com.eloiza.finrag.infrastructure.persistence

import com.eloiza.finrag.PostgresTestContainer
import com.eloiza.finrag.domain.model.Chunk
import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.model.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ApplyExtension(SpringExtension::class)
@SpringBootTest
class DocumentRepositoryJpaAdapterTest(
    jpaUserRepository: JpaUserRepository,
    jpaChunkRepository: JpaChunkRepository,
    adapter: DocumentRepositoryJpaAdapter,
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

        test("salva um documento com seus chunks e lê de volta, preservando o embedding") {
            val userId = persistUser()
            val document = aDocument(userId, chunkCount = 1)
            val chunk = aChunk(document.id, index = 0)

            adapter.save(document, listOf(chunk))

            val found = adapter.findAllByUserId(userId)

            found shouldContainExactly listOf(document)
            val savedChunk = jpaChunkRepository.findById(chunk.id).get()
            savedChunk.embedding.toList() shouldBe chunk.embedding
        }

        test("findAllByUserId retorna só os documentos do usuário informado") {
            val userId = persistUser()
            val otherUserId = persistUser()
            val ownDocument = aDocument(userId, chunkCount = 1)
            val otherDocument = aDocument(otherUserId, chunkCount = 1)

            adapter.save(ownDocument, listOf(aChunk(ownDocument.id, index = 0)))
            adapter.save(otherDocument, listOf(aChunk(otherDocument.id, index = 0)))

            adapter.findAllByUserId(userId) shouldContainExactly listOf(ownDocument)
        }

        test("falha ao salvar um chunk não deixa o documento órfão no banco (rollback transacional)") {
            val userId = persistUser()
            val document = aDocument(userId, chunkCount = 1)
            val chunkComDocumentoInexistente = aChunk(documentId = UUID.randomUUID(), index = 0)

            shouldThrow<DataIntegrityViolationException> {
                adapter.save(document, listOf(chunkComDocumentoInexistente))
            }

            adapter.findAllByUserId(userId).shouldBeEmpty()
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }
}

private fun aDocument(
    userId: UUID,
    chunkCount: Int,
) = Document(
    id = UUID.randomUUID(),
    userId = userId,
    filename = "relatorio.pdf",
    chunkCount = chunkCount,
    createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
)

private fun aChunk(
    documentId: UUID,
    index: Int,
) = Chunk(
    id = UUID.randomUUID(),
    documentId = documentId,
    index = index,
    content = "conteúdo do chunk $index",
    embedding = List(Chunk.EMBEDDING_DIMENSIONS) { position -> position / 1000f },
)

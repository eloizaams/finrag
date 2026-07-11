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

        fun listAll(userId: UUID) = adapter.findAllByUserId(userId, page = 0, size = 20).items

        test("salva um documento com seus chunks e lê de volta, preservando o embedding") {
            val userId = persistUser()
            val document = aDocument(userId, chunkCount = 1)
            val chunk = aChunk(document.id, index = 0)

            adapter.save(document, listOf(chunk))

            val found = listAll(userId)

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

            listAll(userId) shouldContainExactly listOf(ownDocument)
        }

        test("findAllByUserId pagina com totalItems correto e ordenação created_at DESC") {
            val userId = persistUser()
            val documents =
                (0 until 3).map { index ->
                    val document =
                        aDocument(userId, chunkCount = 1)
                            .copy(createdAt = Instant.parse("2026-07-0${index + 1}T00:00:00Z"))
                    adapter.save(document, listOf(aChunk(document.id, index = 0)))
                    document
                }

            val firstPage = adapter.findAllByUserId(userId, page = 0, size = 2)
            val secondPage = adapter.findAllByUserId(userId, page = 1, size = 2)

            firstPage.items shouldContainExactly listOf(documents[2], documents[1])
            firstPage.totalItems shouldBe 3
            firstPage.totalPages shouldBe 2
            secondPage.items shouldContainExactly listOf(documents[0])
        }

        test("findByIdAndUserId retorna o documento quando ele pertence ao usuário") {
            val userId = persistUser()
            val document = aDocument(userId, chunkCount = 1)
            adapter.save(document, listOf(aChunk(document.id, index = 0)))

            adapter.findByIdAndUserId(document.id, userId) shouldBe document
        }

        test("findByIdAndUserId retorna null para documento de outro usuário e para id inexistente") {
            val userId = persistUser()
            val otherUserId = persistUser()
            val document = aDocument(otherUserId, chunkCount = 1)
            adapter.save(document, listOf(aChunk(document.id, index = 0)))

            adapter.findByIdAndUserId(document.id, userId) shouldBe null
            adapter.findByIdAndUserId(UUID.randomUUID(), userId) shouldBe null
        }

        test("deleteByIdAndUserId remove o documento e os chunks vão junto pelo ON DELETE CASCADE") {
            val userId = persistUser()
            val document = aDocument(userId, chunkCount = 2)
            val chunks = listOf(aChunk(document.id, index = 0), aChunk(document.id, index = 1))
            adapter.save(document, chunks)

            val deleted = adapter.deleteByIdAndUserId(document.id, userId)

            deleted shouldBe true
            listAll(userId).shouldBeEmpty()
            chunks.forEach { jpaChunkRepository.findById(it.id).isPresent shouldBe false }
        }

        test("deleteByIdAndUserId não remove documento de outro usuário e retorna false") {
            val userId = persistUser()
            val otherUserId = persistUser()
            val document = aDocument(otherUserId, chunkCount = 1)
            adapter.save(document, listOf(aChunk(document.id, index = 0)))

            val deleted = adapter.deleteByIdAndUserId(document.id, userId)

            deleted shouldBe false
            listAll(otherUserId) shouldContainExactly listOf(document)
        }

        test("falha ao salvar um chunk não deixa o documento órfão no banco (rollback transacional)") {
            val userId = persistUser()
            val document = aDocument(userId, chunkCount = 1)
            val chunkComDocumentoInexistente = aChunk(documentId = UUID.randomUUID(), index = 0)

            shouldThrow<DataIntegrityViolationException> {
                adapter.save(document, listOf(chunkComDocumentoInexistente))
            }

            listAll(userId).shouldBeEmpty()
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

package com.eloiza.finrag.infrastructure.persistence

import com.eloiza.finrag.PostgresTestContainer
import com.eloiza.finrag.domain.exception.EmailAlreadyRegisteredException
import com.eloiza.finrag.domain.model.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ApplyExtension(SpringExtension::class)
@SpringBootTest
class UserRepositoryJpaAdapterTest(
    jpaUserRepository: JpaUserRepository,
) : FunSpec({

        val adapter = UserRepositoryJpaAdapter(jpaUserRepository)

        test("salva um usuário e encontra pelo email") {
            val user = aUser(email = "ana@email.com")

            adapter.save(user)

            adapter.findByEmail("ana@email.com") shouldBe user
        }

        test("retorna null quando o email não existe") {
            adapter.findByEmail("inexistente@email.com").shouldBeNull()
        }

        test("traduz violação da constraint UNIQUE em EmailAlreadyRegisteredException") {
            adapter.save(aUser(email = "duplicado@email.com"))

            shouldThrow<EmailAlreadyRegisteredException> {
                adapter.save(aUser(email = "duplicado@email.com"))
            }
        }
    }) {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres = PostgresTestContainer.instance
    }
}

private fun aUser(email: String) =
    User(
        id = UUID.randomUUID(),
        email = email,
        passwordHash = "hashed:senha123",
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
    )

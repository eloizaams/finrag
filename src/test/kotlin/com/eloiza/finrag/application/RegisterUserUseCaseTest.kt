package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.EmailAlreadyRegisteredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RegisterUserUseCaseTest : FunSpec({
    lateinit var userRepository: FakeUserRepository
    lateinit var passwordHasher: FakePasswordHasher
    lateinit var useCase: RegisterUserUseCase

    beforeTest {
        userRepository = FakeUserRepository()
        passwordHasher = FakePasswordHasher()
        useCase = RegisterUserUseCase(userRepository, passwordHasher)
    }

    test("registra um novo usuário com a senha hasheada, nunca em texto plano") {
        val user = useCase.execute("ana@email.com", "senha123")

        user.email shouldBe "ana@email.com"
        user.passwordHash shouldBe "hashed:senha123"
        userRepository.findByEmail("ana@email.com") shouldBe user
    }

    test("normaliza o email (trim + lowercase) antes de checar duplicidade e salvar") {
        val user = useCase.execute("  Ana@Email.com  ", "senha123")

        user.email shouldBe "ana@email.com"
    }

    test("lança EmailAlreadyRegisteredException quando o email já está cadastrado") {
        useCase.execute("ana@email.com", "senha123")

        shouldThrow<EmailAlreadyRegisteredException> {
            useCase.execute("ANA@EMAIL.COM", "outrasenha")
        }
    }
})

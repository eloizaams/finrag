package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.InvalidCredentialsException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthenticateUserUseCaseTest : FunSpec({
    lateinit var userRepository: FakeUserRepository
    lateinit var passwordHasher: FakePasswordHasher
    lateinit var tokenProvider: FakeTokenProvider
    lateinit var useCase: AuthenticateUserUseCase

    beforeTest {
        userRepository = FakeUserRepository()
        passwordHasher = FakePasswordHasher()
        tokenProvider = FakeTokenProvider()
        useCase = AuthenticateUserUseCase(userRepository, passwordHasher, tokenProvider)

        RegisterUserUseCase(userRepository, passwordHasher).execute("ana@email.com", "senha123")
    }

    test("gera um token quando email e senha estão corretos") {
        val result = useCase.execute("Ana@Email.com", "senha123")

        val user = userRepository.findByEmail("ana@email.com")!!
        result.token shouldBe "token-for-${user.id}"
        result.expiresInSeconds shouldBe 3600
    }

    test("lança InvalidCredentialsException quando o email não existe") {
        shouldThrow<InvalidCredentialsException> {
            useCase.execute("outra@email.com", "senha123")
        }
    }

    test("lança InvalidCredentialsException quando a senha está errada") {
        shouldThrow<InvalidCredentialsException> {
            useCase.execute("ana@email.com", "senhaerrada")
        }
    }
})

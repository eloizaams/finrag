package com.eloiza.finrag.infrastructure.security

import com.eloiza.finrag.domain.model.User
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

private const val TEST_SECRET = "test-secret-at-least-32-bytes-long-1234567890"

class JjwtTokenProviderTest : FunSpec({

    val user =
        User(
            id = UUID.randomUUID(),
            email = "ana@email.com",
            passwordHash = "hashed:senha123",
            createdAt = Instant.now(),
        )

    test("gera um token e consegue validá-lo, extraindo as claims corretas") {
        val provider = JjwtTokenProvider(TEST_SECRET, expirationMinutes = 60)

        val result = provider.generate(user)
        val claims = provider.validate(result.token)

        claims?.userId shouldBe user.id
        claims?.email shouldBe user.email
    }

    test("expiresInSeconds reflete o expiration-minutes configurado") {
        val provider = JjwtTokenProvider(TEST_SECRET, expirationMinutes = 60)

        val result = provider.generate(user)

        result.expiresInSeconds shouldBe 3600L
    }

    test("rejeita token expirado") {
        val provider = JjwtTokenProvider(TEST_SECRET, expirationMinutes = -1)

        val result = provider.generate(user)

        provider.validate(result.token).shouldBeNull()
    }

    test("rejeita token assinado com uma chave diferente") {
        val provider = JjwtTokenProvider(TEST_SECRET, expirationMinutes = 60)
        val otherProvider = JjwtTokenProvider("outra-chave-completamente-diferente-32bytes-xyz", expirationMinutes = 60)

        val result = provider.generate(user)

        otherProvider.validate(result.token).shouldBeNull()
    }

    test("rejeita token malformado") {
        val provider = JjwtTokenProvider(TEST_SECRET, expirationMinutes = 60)

        provider.validate("token-invalido").shouldBeNull()
    }
})

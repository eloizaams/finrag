package com.eloiza.finrag.infrastructure.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BCryptPasswordHasherTest :
    FunSpec({
        val hasher = BCryptPasswordHasher()

        test("hash nunca é igual à senha crua") {
            val hash = hasher.hash("senha123")

            hash shouldNotBe "senha123"
        }

        test("matches retorna true para a senha correta") {
            val hash = hasher.hash("senha123")

            hasher.matches("senha123", hash) shouldBe true
        }

        test("matches retorna false para senha errada") {
            val hash = hasher.hash("senha123")

            hasher.matches("outrasenha", hash) shouldBe false
        }

        test("hashear a mesma senha duas vezes produz hashes diferentes (salt aleatório)") {
            val hash1 = hasher.hash("senha123")
            val hash2 = hasher.hash("senha123")

            hash1 shouldNotBe hash2
        }
    })

package com.eloiza.finrag.application

import com.eloiza.finrag.domain.model.User
import com.eloiza.finrag.domain.port.PasswordHasher
import com.eloiza.finrag.domain.port.TokenClaims
import com.eloiza.finrag.domain.port.TokenProvider
import com.eloiza.finrag.domain.port.TokenResult
import com.eloiza.finrag.domain.port.UserRepository

class FakeUserRepository : UserRepository {
    private val usersByEmail = mutableMapOf<String, User>()

    override fun save(user: User): User {
        usersByEmail[user.email] = user
        return user
    }

    override fun findByEmail(email: String): User? = usersByEmail[email]
}

class FakePasswordHasher : PasswordHasher {
    override fun hash(rawPassword: String): String = "hashed:$rawPassword"

    override fun matches(
        rawPassword: String,
        hashedPassword: String,
    ): Boolean = hash(rawPassword) == hashedPassword
}

class FakeTokenProvider : TokenProvider {
    override fun generate(user: User): TokenResult = TokenResult(token = "token-for-${user.id}", expiresInSeconds = 3600)

    override fun validate(token: String): TokenClaims? = null
}

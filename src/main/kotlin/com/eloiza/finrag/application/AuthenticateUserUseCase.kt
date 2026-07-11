package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.InvalidCredentialsException
import com.eloiza.finrag.domain.port.PasswordHasher
import com.eloiza.finrag.domain.port.TokenProvider
import com.eloiza.finrag.domain.port.TokenResult
import com.eloiza.finrag.domain.port.UserRepository

class AuthenticateUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenProvider: TokenProvider,
) {
    fun execute(
        email: String,
        rawPassword: String,
    ): TokenResult {
        val normalizedEmail = email.trim().lowercase()
        val user = userRepository.findByEmail(normalizedEmail) ?: throw InvalidCredentialsException()

        if (!passwordHasher.matches(rawPassword, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        return tokenProvider.generate(user)
    }
}

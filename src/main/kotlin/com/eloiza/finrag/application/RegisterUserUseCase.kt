package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.EmailAlreadyRegisteredException
import com.eloiza.finrag.domain.model.User
import com.eloiza.finrag.domain.port.PasswordHasher
import com.eloiza.finrag.domain.port.UserRepository
import java.time.Instant
import java.util.UUID

class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
) {
    fun execute(
        email: String,
        rawPassword: String,
    ): User {
        val normalizedEmail = email.trim().lowercase()

        if (userRepository.findByEmail(normalizedEmail) != null) {
            throw EmailAlreadyRegisteredException(normalizedEmail)
        }

        val user =
            User(
                id = UUID.randomUUID(),
                email = normalizedEmail,
                passwordHash = passwordHasher.hash(rawPassword),
                createdAt = Instant.now(),
            )

        return userRepository.save(user)
    }
}

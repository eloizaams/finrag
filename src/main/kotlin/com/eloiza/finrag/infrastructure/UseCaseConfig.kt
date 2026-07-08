package com.eloiza.finrag.infrastructure

import com.eloiza.finrag.application.AuthenticateUserUseCase
import com.eloiza.finrag.application.RegisterUserUseCase
import com.eloiza.finrag.domain.port.PasswordHasher
import com.eloiza.finrag.domain.port.TokenProvider
import com.eloiza.finrag.domain.port.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfig {
    @Bean
    fun registerUserUseCase(
        userRepository: UserRepository,
        passwordHasher: PasswordHasher,
    ): RegisterUserUseCase = RegisterUserUseCase(userRepository, passwordHasher)

    @Bean
    fun authenticateUserUseCase(
        userRepository: UserRepository,
        passwordHasher: PasswordHasher,
        tokenProvider: TokenProvider,
    ): AuthenticateUserUseCase = AuthenticateUserUseCase(userRepository, passwordHasher, tokenProvider)
}

package com.eloiza.finrag.api

import com.eloiza.finrag.api.dto.LoginRequest
import com.eloiza.finrag.api.dto.LoginResponse
import com.eloiza.finrag.api.dto.RegisterRequest
import com.eloiza.finrag.api.dto.RegisterResponse
import com.eloiza.finrag.application.AuthenticateUserUseCase
import com.eloiza.finrag.application.RegisterUserUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val authenticateUserUseCase: AuthenticateUserUseCase,
) {
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): ResponseEntity<RegisterResponse> {
        val user = registerUserUseCase.execute(request.email, request.password)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(RegisterResponse(id = user.id, email = user.email))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): LoginResponse {
        val result = authenticateUserUseCase.execute(request.email, request.password)

        return LoginResponse(accessToken = result.token, expiresIn = result.expiresInSeconds)
    }
}

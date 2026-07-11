package com.eloiza.finrag.api

import com.eloiza.finrag.api.dto.LoginRequest
import com.eloiza.finrag.api.dto.LoginResponse
import com.eloiza.finrag.api.dto.RegisterRequest
import com.eloiza.finrag.api.dto.RegisterResponse
import com.eloiza.finrag.application.AuthenticateUserUseCase
import com.eloiza.finrag.application.RegisterUserUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Registro e login — únicos endpoints públicos da API")
@SecurityRequirements // anula o requisito global de bearerAuth: /auth não exige token
class AuthController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val authenticateUserUseCase: AuthenticateUserUseCase,
) {
    @Operation(
        summary = "Registra um novo usuário",
        description = "Cria o usuário a partir de email e senha (mínimo 8 caracteres). A senha nunca aparece na resposta.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Usuário criado"),
        ApiResponse(responseCode = "400", description = "Email malformado ou senha curta demais (ProblemDetail)"),
        ApiResponse(responseCode = "409", description = "Email já registrado (ProblemDetail)"),
    )
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): ResponseEntity<RegisterResponse> {
        val user = registerUserUseCase.execute(request.email, request.password)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(RegisterResponse(id = user.id, email = user.email))
    }

    @Operation(
        summary = "Autentica e emite um token JWT",
        description =
            "Retorna o access token (Bearer) e a expiração em segundos. " +
                "Use o token no botão Authorize para chamar os endpoints protegidos.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Token emitido"),
        ApiResponse(
            responseCode = "401",
            description = "Credenciais inválidas — mesma resposta para email inexistente e senha errada (ProblemDetail)",
        ),
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): LoginResponse {
        val result = authenticateUserUseCase.execute(request.email, request.password)

        return LoginResponse(accessToken = result.token, expiresIn = result.expiresInSeconds)
    }
}

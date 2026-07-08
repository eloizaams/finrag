# M1 — Auth JWT — Tasks

## Domínio e portas

- [x] Criar `domain/model/User.kt`
- [x] Criar `domain/port/UserRepository.kt` (interface: `save`, `findByEmail`)
- [x] Criar `domain/port/PasswordHasher.kt` (interface: `hash`, `matches`)
- [x] Criar `domain/port/TokenProvider.kt` (interface: `generate`, `validate`)
- [x] Criar exceções de domínio: `EmailAlreadyRegisteredException`,
      `InvalidCredentialsException`

## Casos de uso

- [x] Implementar `application/RegisterUserUseCase.kt` (+ teste unitário com
      fake de `UserRepository`/`PasswordHasher`)
- [x] Implementar `application/AuthenticateUserUseCase.kt` (+ teste unitário
      com fake de `UserRepository`/`PasswordHasher`/`TokenProvider`)

## Infraestrutura — persistência

- [ ] Adicionar dependências: `spring-boot-starter-security`,
      `spring-boot-starter-validation`, `jjwt-api`/`jjwt-impl`/`jjwt-jackson`
- [ ] Criar migration `V2__create_users_table.sql`
- [ ] Criar `infrastructure/persistence/UserEntity.kt` (JPA)
- [ ] Criar `infrastructure/persistence/UserRepositoryJpaAdapter.kt`
- [ ] Teste de integração (Testcontainers) do adapter: salvar e buscar por
      email, violação de unique constraint em email duplicado

## Infraestrutura — segurança

- [ ] Implementar `infrastructure/security/BCryptPasswordHasher.kt`
- [ ] Implementar `infrastructure/security/JjwtTokenProvider.kt` (gerar token
      com claims `sub`/`email`/`iat`/`exp`; validar assinatura + expiração)
- [ ] Teste unitário do `JjwtTokenProvider`: gera e valida token válido, rejeita
      token expirado, rejeita token com assinatura errada
- [ ] Configurar `finrag.jwt.secret` e `finrag.jwt.expiration-minutes` no
      `application.yaml`, lendo de variável de ambiente
- [ ] Validar na inicialização que `JWT_SECRET` tem tamanho mínimo (falhar
      rápido se não tiver)
- [ ] Implementar `infrastructure/security/JwtAuthenticationFilter.kt`
- [ ] Implementar `infrastructure/security/SecurityConfig.kt` (stateless, CSRF
      off, `/auth/**` e `/actuator/health` públicos, resto autenticado)

## API

- [ ] Criar DTOs: `RegisterRequest`/`RegisterResponse`,
      `LoginRequest`/`LoginResponse`, com Bean Validation
- [ ] Implementar `api/AuthController.kt` (`POST /auth/register`,
      `POST /auth/login`)
- [ ] Implementar `api/SecurityExceptionHandler.kt` (`@ControllerAdvice` →
      `ProblemDetail` para 400/401/409)

## Testes de integração (Kotest + Testcontainers)

- [ ] Registro com sucesso → `201` + corpo sem senha
- [ ] Registro com email duplicado → `409`
- [ ] Registro com payload inválido (email malformado, senha curta) → `400`
- [ ] Login com sucesso → `200` + `accessToken` presente
- [ ] Login com senha errada → `401`, mensagem genérica
- [ ] Login com email inexistente → `401`, mesma mensagem genérica do caso
      anterior
- [ ] Acesso a rota protegida sem header `Authorization` → `401`
- [ ] Acesso a rota protegida com token válido → passa pelo filtro (usar rota
      dummy/teste se M2 ainda não existir)
- [ ] Acesso a rota protegida com token expirado/malformado → `401`
- [ ] `GET /actuator/health` continua público (sem token) → `200`

## Fechamento do marco

- [ ] Rodar `./gradlew build` limpo (build + todos os testes)
- [ ] Atualizar README se necessário (novos endpoints, nova env var
      `JWT_SECRET`)
- [ ] Commit(s) semânticos ao longo da implementação (ex.:
      `feat(auth): adicionar registro e login com JWT`)
- [ ] Abrir PR de `feature/m1-auth-jwt` para `develop`

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M1-requirements.md` atendidos, testes de
integração cobrindo os fluxos listados acima, CI verde no PR.

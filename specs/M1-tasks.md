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

> Backfill do M0 aplicado junto: o `build.gradle.kts` não tinha JPA/Flyway/
> Postgres driver/Testcontainers ainda (M0 nunca chegou a ser implementado).
> `docker-compose.yml`/`Dockerfile`/CI continuam pendências do M0.

- [x] Adicionar dependências: `spring-boot-starter-data-jpa`,
      `spring-boot-starter-flyway`, `flyway-database-postgresql`, driver
      `postgresql`, `spring-boot-testcontainers` +
      `testcontainers-junit-jupiter`/`testcontainers-postgresql`
- [x] Criar migration `V1__enable_pgvector.sql` (M0) e
      `V2__create_users_table.sql` (M1)
- [x] Criar `infrastructure/persistence/UserEntity.kt` (JPA)
- [x] Criar `infrastructure/persistence/UserRepositoryJpaAdapter.kt`
- [x] Teste de integração (Testcontainers, `pgvector/pgvector:pg16`) do
      adapter: salvar e buscar por email, retorno nulo para email inexistente,
      violação de unique constraint em email duplicado

## Infraestrutura — segurança

- [x] Adicionar dependência `spring-boot-starter-security`
- [x] Adicionar dependências `jjwt-api`/`jjwt-impl`/`jjwt-jackson`
- [x] Adicionar dependência `spring-boot-starter-validation`
- [x] Implementar `infrastructure/security/BCryptPasswordHasher.kt` (+ teste
      unitário: hash difere da senha crua, matches true/false, salt aleatório)
- [x] Implementar `infrastructure/security/JjwtTokenProvider.kt` (gerar token
      com claims `sub`/`email`/`iat`/`exp`; validar assinatura + expiração)
- [x] Teste unitário do `JjwtTokenProvider`: gera e valida token válido, rejeita
      token expirado, rejeita token com assinatura errada, rejeita token
      assinado com chave diferente, rejeita token malformado
- [x] Configurar `finrag.jwt.secret` e `finrag.jwt.expiration-minutes` no
      `application.yaml`, lendo de variável de ambiente
- [x] Validar na inicialização que `JWT_SECRET` tem tamanho mínimo (falhar
      rápido se não tiver) — de graça via `Keys.hmacShaKeyFor` do jjwt, que
      lança `WeakKeyException` na construção da chave se for curta demais
- [x] Implementar `infrastructure/security/JwtAuthenticationFilter.kt`
- [x] Implementar `infrastructure/security/SecurityConfig.kt` (stateless, CSRF
      off, `/auth/**`, `/actuator/health` e `/error` públicos, resto
      autenticado; `AuthenticationEntryPoint` explícito para responder `401`
      em vez do `403` padrão do Spring Security quando não há
      `httpBasic`/`formLogin` configurado)
- [x] Teste de integração (Testcontainers) cobrindo o filtro + regras de
      autorização: `/actuator/health` sem token, rota protegida sem token
      (`401`), com token malformado (`401`), com token válido (autentica e
      chega a dar `404` de rota inexistente, não `401`)

## API

- [x] Criar DTOs: `RegisterRequest`/`RegisterResponse`,
      `LoginRequest`/`LoginResponse`, com Bean Validation
- [x] Implementar `api/AuthController.kt` (`POST /auth/register`,
      `POST /auth/login`)
- [x] Implementar `api/SecurityExceptionHandler.kt` (`@RestControllerAdvice` →
      `ProblemDetail` para 401/409; `400` de validação sai de graça via
      `spring.mvc.problemdetails.enabled=true`)
- [x] Criar `infrastructure/UseCaseConfig.kt` (composition root: os use cases
      não têm anotação Spring de propósito, então precisam de `@Bean`
      explícito em algum lugar da camada de infra)

## Testes de integração (Kotest + Testcontainers)

- [x] Registro com sucesso → `201` + corpo sem senha
- [x] Registro com email duplicado → `409`
- [x] Registro com payload inválido (email malformado, senha curta) → `400`
- [x] Login com sucesso → `200` + `accessToken` presente
- [x] Login com senha errada → `401`, mensagem genérica
- [x] Login com email inexistente → `401`, mesma mensagem genérica do caso
      anterior
- [x] Acesso a rota protegida sem header `Authorization` → `401` (coberto em
      `SecurityConfigTest`)
- [x] Acesso a rota protegida com token válido → passa pelo filtro (coberto em
      `SecurityConfigTest`, usando rota dummy já que M2 ainda não existe)
- [x] Acesso a rota protegida com token expirado/malformado → `401` (coberto
      em `SecurityConfigTest`/`JjwtTokenProviderTest`)
- [x] `GET /actuator/health` continua público (sem token) → `200`

## Fechamento do marco

- [x] Rodar `./gradlew build` limpo (build + todos os testes) — 31 testes
      passando
- [x] Atualizar README (endpoints `/auth/register`/`/auth/login` com exemplos
      de `curl`, env var `JWT_SECRET` obrigatória ao rodar sem Docker Compose)
- [x] Validar fluxo completo via `docker compose up` de verdade (não só
      testes): registro, duplicado, payload inválido, login, senha errada,
      rota protegida com/sem token — todos com o status e corpo esperados
- [x] Commit(s) semânticos ao longo da implementação
- [ ] Abrir PR de `feature/m1-auth-jwt` para `develop`

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M1-requirements.md` atendidos, testes de
integração cobrindo os fluxos listados acima, CI verde no PR.

# M1 — Auth JWT — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Biblioteca JWT | `jjwt` (io.jsonwebtoken) — geração/validação manual | `spring-boot-starter-oauth2-resource-server` assume um *issuer* externo (JWK endpoint), feito para validar tokens emitidos por um IdP terceiro. Aqui a própria API emite e valida — implementar na mão segue a mesma lógica do ADR-04 (pipeline RAG manual): mais simples para um único serviço e dá para explicar cada etapa (claims, assinatura, parsing) em entrevista |
| Algoritmo de assinatura | HS256 (simétrico) | Só este serviço emite e valida tokens — não há necessidade de separar chave pública/privada como em RS256. Segredo único via variável de ambiente é suficiente e mais simples |
| Hash de senha | `BCryptPasswordEncoder` (Spring Security), strength padrão (10) | Padrão de mercado, resistente a ataques de força bruta por ser deliberadamente lento; não reinventar isso |
| Expiração do access token | 60 minutos | Sem refresh token no v1 (fora de escopo), então precisa ser longo o bastante para não incomodar durante o uso normal da API, mas curto o bastante para limitar o estrago de um token vazado. Reavaliar se M9 trouxer refresh token |
| Sessão | Stateless (`SessionCreationPolicy.STATELESS`) | Coerente com API REST sem estado; toda informação de identidade vem no próprio token a cada requisição |
| CSRF | Desabilitado | CSRF protege contra abuso de cookies de sessão automáticos pelo navegador; aqui não há cookie de sessão, o cliente envia o token explicitamente no header — não se aplica |
| `register` retorna token? | Não — retorna só os dados do usuário (`id`, `email`). Login é uma chamada separada | Mantém cada endpoint com uma única responsabilidade (criar usuário vs. autenticar) e simplifica os testes de cada fluxo isoladamente. Custo: mais uma chamada no fluxo de onboarding, aceitável para o escopo do projeto |
| Formato de erro | `ProblemDetail` (RFC 7807), nativo do Spring Boot 4 | Já vem pronto no framework, evita inventar um formato de erro customizado, e é o padrão atual — bom para demonstrar conhecimento de recursos recentes do Spring |
| Claims do token | `sub` (id do usuário), `email`, `iat`, `exp` | O suficiente para identificar o usuário nas próximas requisições (M2/M3 vão usar o `sub` para filtrar documentos). Sem claim de `role` — só existe um tipo de usuário neste marco, adicionar `role` agora seria especular sobre requisito futuro |

## Onde entra cada peça (Clean Architecture)

```
domain/
├── model/
│   └── User.kt                    # id, email, passwordHash — sem lógica de framework
└── port/
    ├── UserRepository.kt          # interface: save, findByEmail
    ├── PasswordHasher.kt          # interface: hash(raw), matches(raw, hash)
    └── TokenProvider.kt           # interface: generate(user), validate(token) -> claims

application/
├── RegisterUserUseCase.kt         # valida email único, hasheia senha, persiste
└── AuthenticateUserUseCase.kt     # busca usuário, valida senha, gera token

infrastructure/
├── persistence/
│   ├── UserEntity.kt              # entidade JPA
│   └── UserRepositoryJpaAdapter.kt # implementa domain/port/UserRepository
└── security/
    ├── BCryptPasswordHasher.kt    # implementa domain/port/PasswordHasher
    ├── JjwtTokenProvider.kt       # implementa domain/port/TokenProvider
    ├── JwtAuthenticationFilter.kt # OncePerRequestFilter, lê header, valida, popula SecurityContext
    └── SecurityConfig.kt          # SecurityFilterChain: rotas públicas, stateless, filtro JWT

api/
├── AuthController.kt              # POST /auth/register, POST /auth/login
├── dto/
│   ├── RegisterRequest.kt / RegisterResponse.kt
│   └── LoginRequest.kt / LoginResponse.kt
└── SecurityExceptionHandler.kt    # @ControllerAdvice -> ProblemDetail para 401/409/400
```

**Por quê separar `PasswordHasher` e `TokenProvider` em `domain/port`**: os casos de
uso (`RegisterUserUseCase`, `AuthenticateUserUseCase`) não podem depender de
`BCryptPasswordEncoder` do Spring nem de `jjwt` diretamente — isso violaria a regra
de dependência do projeto (domain/application não dependem de infra). Também é o
que permite testar os use cases com fakes, sem subir Spring Security nem
Testcontainers.

## Fluxo de registro

1. `AuthController` recebe `RegisterRequest { email, password }`, valida com
   Bean Validation (`@Email`, `@NotBlank`, `@Size(min = 8)`)
2. `RegisterUserUseCase` chama `UserRepository.findByEmail` — se existir, lança
   exceção de domínio (`EmailAlreadyRegisteredException`) → mapeada para `409`
3. Hasheia a senha via `PasswordHasher.hash(raw)`, monta `User`, persiste via
   `UserRepository.save`
4. Controller retorna `201` com `RegisterResponse { id, email }`

## Fluxo de login

1. `AuthController` recebe `LoginRequest { email, password }`
2. `AuthenticateUserUseCase` busca o usuário por email; se não existir, lança
   `InvalidCredentialsException`
3. Compara senha via `PasswordHasher.matches(raw, hash)`; se não bater, lança a
   **mesma** `InvalidCredentialsException` (não diferenciar "usuário não
   existe" de "senha errada" na resposta)
4. Se ok, `TokenProvider.generate(user)` monta o JWT
5. Controller retorna `200` com `LoginResponse { accessToken, tokenType:
   "Bearer", expiresIn }`

## Fluxo de autenticação de requisição protegida

1. `JwtAuthenticationFilter` intercepta toda requisição, procura header
   `Authorization: Bearer <token>`
2. Sem header → segue a cadeia sem autenticar; `SecurityConfig` decide se a
   rota exige autenticação (rotas fora de `/auth/**` e `/actuator/health` são
   `authenticated()`, então cai em `401` mais adiante)
3. Com header → `TokenProvider.validate(token)` faz parse e checa assinatura +
   expiração. Se válido, popula o `SecurityContextHolder` com o `userId` das
   claims. Se inválido/expirado, não autentica (deixa o filtro de exceção do
   Spring Security responder `401`)

## Migration Flyway (V2)

`users` table — a extensão pgvector já foi habilitada em `V1__enable_pgvector.sql`
(M0), então esta é a primeira tabela de domínio do projeto:

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

> `gen_random_uuid()` requer a extensão `pgcrypto`. Alternativa mais simples:
> gerar o UUID na aplicação (`UUID.randomUUID()`) antes de persistir, evitando
> depender de mais uma extensão do Postgres — provavelmente a escolha mais
> simples para este projeto. Decidir ao implementar V2.

## Dependências novas (build.gradle.kts)

- `spring-boot-starter-security`
- `spring-boot-starter-validation` (Bean Validation nos DTOs)
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl` (runtime), `jjwt-jackson` (runtime)

## Configuração (application.yaml)

```yaml
finrag:
  jwt:
    secret: ${JWT_SECRET}            # obrigatório via env var, sem default em prod
    expiration-minutes: ${JWT_EXPIRATION_MINUTES:60}
```

`JWT_SECRET` precisa ter pelo menos 256 bits (32 bytes) para HS256 — validar na
inicialização da aplicação (falhar rápido se o segredo for curto demais).

## Trade-offs conscientes

- **Sem refresh token**: usuário precisa logar de novo a cada hora. Aceitável
  para portfólio/demo; documentado como candidato ao M9 se o projeto crescer.
- **Mensagem de erro genérica no login**: um pouco pior para UX (não diz qual
  campo errou), mas evita enumeration attack (descobrir quais emails estão
  cadastrados via tentativa de login) — troca deliberada em favor de segurança.
- **HS256 em vez de RS256**: mais simples de operar (um segredo só), mas
  significa que qualquer serviço que precise *validar* o token também precisa
  conhecer o segredo que *assina* — sem problema aqui porque é um serviço só;
  viraria um problema se o sistema crescesse para múltiplos serviços
  desacoplados validando tokens de forma independente.

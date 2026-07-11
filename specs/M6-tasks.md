# M6 — Docs da API + hardening — Tasks

## Infraestrutura — OpenAPI/docs

- [x] Adicionar dependência `springdoc-openapi-starter-webmvc-ui:3.0.3`
- [x] Criar `infrastructure/openapi/OpenApiConfig.kt` (info da API + security
      scheme `bearerAuth` HTTP bearer/JWT)
- [x] Liberar `/swagger-ui/**` e `/v3/api-docs/**` no `SecurityConfig`
- [ ] Validar manualmente: spec em `/v3/api-docs` e UI navegável com "Authorize"
      funcionando de ponta a ponta (login → token → chamada autenticada)

## API — anotações de documentação

- [x] Anotar `AuthController` (`@Operation`, `@ApiResponses` com os erros
      401/409/400 documentados)
- [x] Anotar `DocumentController` (upload multipart, listagem paginada, GET/DELETE
      por id com 404/400/415/422/413/429)
- [x] Anotar `QuestionController` (200 com fontes vazias documentado como
      não-erro; 400/502/429)
- [x] Marcar `/auth/**` sem requisito de segurança e o restante com `bearerAuth`

## Infraestrutura — rate limiting

- [x] Adicionar dependência `bucket4j_jdk17-core:8.14.0`
- [x] Criar `infrastructure/ratelimit/RateLimitProperties.kt`
      (`@ConfigurationProperties(finrag.rate-limit)`, capacity/period por rota)
- [x] Configurar `finrag.rate-limit.*` no `application.yaml` (defaults 10/min
      perguntas, 5/min uploads, sobrescrevíveis por env var)
- [x] Implementar `infrastructure/ratelimit/RateLimitFilter.kt` (token bucket
      por `userId`+rota via `ConcurrentHashMap`, 429 `ProblemDetail` +
      `Retry-After`, counter `finrag.ratelimit.rejections{endpoint}`)
- [x] Registrar o filtro após o `JwtAuthenticationFilter` no `SecurityConfig`
- [x] Teste unitário do filtro/buckets com relógio controlado (`TimeMeter`
      custom): consome até estourar, refill devolve tokens, chaves isoladas

## Validações de entrada

- [x] `@Size(max = 2000)` na `QuestionRequest` (+ mensagem clara no 400)

## Testes de integração (Kotest + Testcontainers)

- [x] `GET /v3/api-docs` sem token → `200` e contém os paths dos endpoints
      existentes
- [x] `GET /swagger-ui.html` sem token → acessível (200/redirect da UI)
- [x] Exceder o limite de `POST /questions` → `429` com `Retry-After` presente
      e `ProblemDetail` no corpo; requisição não chega ao provedor (fake não
      é chamado)
- [x] Limite de um usuário não afeta outro (isolamento por `userId`)
- [x] Após o período de refill, o usuário volta a ser atendido (usar limite
      curto via propriedade de teste)
- [x] Exceder o limite de `POST /documents` → `429`
- [x] `429` incrementa `finrag.ratelimit.rejections`
- [x] Pergunta acima de 2000 caracteres → `400` com detalhe do campo, sem
      chamar o provedor de embeddings
- [ ] Rotas públicas (`/actuator/health`, `/actuator/prometheus`) seguem `200`
      sem token (não regressão)

## Fechamento do marco

- [ ] Rodar `./gradlew build` limpo (build + todos os testes)
- [ ] Atualizar README (seção de docs da API com link do Swagger UI, seção de
      rate limiting com limites e env vars) e progresso em
      `00-architecture.md`/`01-roadmap.md`
- [ ] Validar fluxo completo via `docker compose up`: Swagger UI no browser,
      authorize + chamada autenticada, estourar limite de perguntas (429 +
      Retry-After), pergunta gigante (400)
- [ ] Commit(s) semânticos ao longo da implementação
- [ ] Abrir PR de `feature/m6-docs-hardening` para `develop`

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M6-requirements.md` atendidos, testes de
integração cobrindo os fluxos listados acima, CI verde no PR.

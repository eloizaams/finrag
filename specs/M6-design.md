# M6 — Docs da API + hardening — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Biblioteca de docs | `springdoc-openapi-starter-webmvc-ui` **3.0.3** | Padrão de fato para OpenAPI em Spring Boot; a linha 3.x é a compatível com Boot 4.x na matriz oficial do projeto (2.8.x atende só até Boot 3.5). Gera spec + serve Swagger UI com uma dependência |
| Estilo de documentação | Anotações (`@Operation`, `@ApiResponses`) nos controllers + `OpenApiConfig` programática para o global (info da API, security scheme) | O objetivo do marco é quem testa **sem ler o código** — doc rica com descrições e códigos de erro vale a verbosidade extra na camada `api`. O que é global (título, versão, JWT) fica em um `@Configuration` só |
| Auth na doc | Security scheme `bearerAuth` (HTTP bearer, formato JWT) global; `/auth/**` marcado sem requisito | Habilita o botão "Authorize" do Swagger UI: cola o token uma vez e chama qualquer endpoint dali. Espelha exatamente o comportamento real da API |
| Swagger em produção | Sempre público (`/swagger-ui/**` e `/v3/api-docs/**` no `permitAll`) | O propósito do portfólio é recrutador testar pela UI; a spec não expõe segredo e os endpoints continuam atrás de JWT + rate limit |
| Rate limiting | **Bucket4j 8.14.0** (`com.bucket4j:bucket4j_jdk17-core`) | Biblioteca de referência para token bucket na JVM, com a concorrência resolvida (mesmo critério do BCrypt no M1: não reinventar primitivas sensíveis). Artefato `_jdk17` é a linha atual para JDK 17+ (projeto usa 21) |
| Algoritmo | Token bucket com refill "greedy" (capacidade volta distribuída ao longo da janela) | Permite rajada curta legítima (balde cheio) sem permitir abuso sustentado, e não tem o pico de borda da janela fixa. Mesmo modelo das APIs da OpenAI/Anthropic — bom paralelo em entrevista |
| Limites padrão | `POST /questions`: 10/min; `POST /documents`: 5/min — por usuário | Uso interativo real fica muito abaixo disso; upload é mais caro (extração + N embeddings) e por isso mais restrito. Configuráveis via env var sem recompilar |
| Onde o limiter entra | `RateLimitFilter` (`OncePerRequestFilter`) em `infrastructure/ratelimit`, registrado **depois** do `JwtAuthenticationFilter` | Preocupação transversal de borda fica na borda: o filtro usa o `userId` já autenticado como chave e os casos de uso nem sabem que ele existe. `domain`/`application` intocados neste marco |
| Estado dos buckets | `ConcurrentHashMap<chave, Bucket>` em memória, chave = `userId` + rota | Uma instância única no free tier do M7 — não precisa de estado distribuído. Restart zera os baldes (aceitável; janela é de 1 min) |
| Resposta do `429` | `ProblemDetail` + header `Retry-After` em segundos (do `nanosToWaitForRefill` do probe) | `Retry-After` é o header padrão HTTP para `429`/`503` — clientes e SDKs já o entendem; a família `X-RateLimit-*` é convenção extra que pode evoluir depois |
| Métrica | Counter `finrag.ratelimit.rejections` com tag `endpoint` | Continuidade do M4: em produção, responde "o limite está apertado demais?" com dado real |
| Tamanho máximo da pergunta | `@Size(max = 2000)` na `QuestionRequest` | Perguntas reais têm dezenas/centenas de caracteres — 2000 dá folga ampla e corta o vetor de custo (pergunta gigante → embedding + prompt caros) antes de sair da API. Erro `400` de graça via Bean Validation |

## Onde entra cada peça (Clean Architecture)

```
domain/          (intocado neste marco)
application/     (intocado neste marco)

infrastructure/
├── openapi/
│   └── OpenApiConfig.kt           # bean OpenAPI: título/descrição/versão, security scheme bearerAuth
├── ratelimit/
│   ├── RateLimitProperties.kt     # @ConfigurationProperties(finrag.rate-limit): capacity/período por rota
│   └── RateLimitFilter.kt         # OncePerRequestFilter: resolve a regra da rota, consome token do
│                                  #   bucket do usuário, 429 + Retry-After + métrica quando estoura
└── security/
    └── SecurityConfig.kt          # (alterado) permitAll para /swagger-ui/** e /v3/api-docs/**;
                                   #   registra RateLimitFilter após o JwtAuthenticationFilter

api/
├── AuthController.kt              # (alterado) +@Operation/@ApiResponses por endpoint
├── DocumentController.kt          # (alterado) idem
├── QuestionController.kt          # (alterado) idem
└── dto/QuestionRequest.kt         # (alterado) +@Size(max = 2000)
```

**Por que nada muda em `domain`/`application`**: docs e rate limiting são
preocupações de borda HTTP — regra de negócio nenhuma depende delas. É o
argumento inverso do `PageResult` no M5 (que era contrato da porta): aqui a
camada interna não precisa saber que existe quota.

## Fluxo — requisição com rate limit

1. `JwtAuthenticationFilter` autentica e popula o `SecurityContext` com o `userId`
2. `RateLimitFilter` verifica se a rota tem regra (`POST /documents`,
   `POST /questions`); sem regra → segue a cadeia sem custo
3. Com regra: busca (ou cria) o bucket do par `userId` + rota no
   `ConcurrentHashMap` e tenta consumir 1 token (`tryConsumeAndReturnRemaining`)
4. Token disponível → segue para o controller normalmente
5. Sem token → responde `429` com `ProblemDetail`, header `Retry-After`
   (segundos até o próximo token, arredondado para cima) e incrementa
   `finrag.ratelimit.rejections{endpoint=...}` — a requisição **não** chega ao
   caso de uso nem às APIs pagas
6. Sem usuário autenticado o filtro não se aplica (as rotas limitadas já exigem
   JWT — sem token o Security responde `401` antes)

## Fluxo — documentação

1. `springdoc` escaneia os controllers na inicialização e monta a spec OpenAPI 3
2. `GET /v3/api-docs` serve o JSON; `GET /swagger-ui.html` serve a UI — ambos
   públicos no `SecurityConfig`
3. Na UI, "Authorize" recebe o JWT (obtido via `POST /auth/login` na própria UI)
   e injeta o `Authorization: Bearer` em todas as chamadas seguintes

## Migration Flyway

Nenhuma.

## Dependências novas (build.gradle.kts)

- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3`
- `com.bucket4j:bucket4j_jdk17-core:8.14.0`

## Configuração (application.yaml)

```yaml
finrag:
  rate-limit:
    questions:
      capacity: ${RATE_LIMIT_QUESTIONS_CAPACITY:10}
      period-seconds: ${RATE_LIMIT_QUESTIONS_PERIOD_SECONDS:60}
    documents:
      capacity: ${RATE_LIMIT_DOCUMENTS_CAPACITY:5}
      period-seconds: ${RATE_LIMIT_DOCUMENTS_PERIOD_SECONDS:60}
```

(springdoc funciona com os defaults; ajustes de UI só se necessário)

## Trade-offs conscientes

- **Limite em memória, por instância**: restart zera os baldes e múltiplas
  instâncias multiplicariam o limite efetivo. Correto para o alvo (1 instância
  no free tier); a evolução natural é `bucket4j-redis`, registrada para o M9.
- **Mapa de buckets sem eviction**: cresce com usuários ativos (um `Bucket`
  por usuário/rota é minúsculo). Se doesse, trocar por cache com
  `expireAfterAccess` (Caffeine) — desnecessário no escopo atual.
- **Anotações OpenAPI nos controllers**: verbosidade real na camada `api` em
  troca de doc completa — escolha deliberada dado o objetivo do marco.
- **Sem `X-RateLimit-*`**: só `Retry-After`. Suficiente para clientes se
  comportarem; a família estendida entra se algum consumidor real precisar.
- **Limite por usuário, não por IP**: os endpoints limitados exigem JWT, então
  a chave natural é o `userId` (IP compartilhado por NAT puniria inocentes).
  Custo: quem criar várias contas multiplica a quota — aceitável sem verificação
  de email (fora de escopo desde o M1).

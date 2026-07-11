# M6 — Docs da API + hardening — Requirements

## Objetivo do marco

Preparar a API para a exposição pública do M7: documentação interativa
(OpenAPI/Swagger UI) para quem for testar o portfólio sem ler o código, rate
limiting nos endpoints que disparam custo real em APIs pagas (`POST /documents`
→ OpenAI, `POST /questions` → OpenAI + Anthropic) e refino das validações de
entrada que hoje deixam passar payloads abusivos. Sem isso, o deploy do M7
exporia custo de API sem proteção — dependência registrada no roadmap.

## Critérios de aceite

1. A spec OpenAPI 3 é gerada automaticamente a partir do código e servida em
   endpoint público (ex.: `/v3/api-docs`), junto com Swagger UI navegável —
   ambos acessíveis sem token.
2. Todos os endpoints existentes aparecem documentados com modelos de
   request/response e códigos de status de sucesso e de erro (`ProblemDetail`).
3. Endpoints autenticados aparecem com esquema de segurança Bearer JWT: dá para
   colar o token no "Authorize" da UI e chamar qualquer endpoint dali mesmo.
4. `POST /documents` e `POST /questions` têm rate limiting **por usuário
   autenticado**; exceder o limite retorna `429 Too Many Requests` com
   `ProblemDetail` e header indicando quando tentar de novo (formato no design).
5. O limite de um usuário não consome o de outro; requisições dentro do limite
   nunca recebem `429`.
6. Limites configuráveis via `application.yaml`/variável de ambiente, sem
   recompilar (valores padrão definidos no design.md).
7. `POST /questions` valida tamanho máximo da pergunta (valor no design):
   acima do limite retorna `400` com detalhe do campo, **sem chamar a OpenAI**.
8. Rotas públicas existentes (`/auth/**`, `/actuator/health`,
   `/actuator/prometheus`) continuam funcionando — os critérios do M0/M1/M4
   não regridem.
9. Testes de integração cobrem pelo menos: api-docs acessível sem token e
   listando os endpoints, `429` ao exceder o limite, isolamento do limite entre
   usuários, recuperação do limite após a janela/refill, e pergunta acima do
   tamanho máximo → `400`.

## Fora de escopo neste marco

- Rate limiting distribuído (Redis/gateway) — limite em memória por instância
  basta para uma instância única no free tier do M7
- Bloqueio de conta / lockout por tentativas de login falhas (M1 já deixou fora)
- API keys, planos ou quotas diferenciadas por usuário
- Versionamento de API (`/v1/...`)
- CORS para frontend em browser — não há frontend na v1

## Perguntas em aberto (decidir no design.md)

- Biblioteca de docs: `springdoc-openapi` (padrão de fato para Spring Boot) —
  qual starter/versão compatível com Boot 4? Documentar via anotações nos
  controllers ou configuração programática (quanto "poluir" a camada `api`)?
- Biblioteca de rate limiting: Bucket4j, Resilience4j RateLimiter, ou
  implementação própria de token bucket (na linha do ADR-04, "manual para
  saber explicar")?
- Algoritmo/semântica: token bucket (permite rajada e refill contínuo) vs
  janela fixa? Quais limites padrão (ex.: X perguntas/min, Y uploads/min)?
- Onde o limiter entra: `OncePerRequestFilter`/interceptor (antes do
  controller) vs verificação no caso de uso? E em qual camada da Clean
  Architecture mora a regra?
- Header de resposta do `429`: `Retry-After` ou família `X-RateLimit-*`?
- Tamanho máximo da pergunta: qual valor faz sentido dado o pipeline
  (embedding aceita ~8k tokens; chunks têm ~1000 chars)?
- Swagger UI sempre público ou desligável via config para produção?

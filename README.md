# FinRAG

API REST em Kotlin + Spring Boot com um pipeline RAG (Retrieval-Augmented Generation)
sobre documentos financeiros. Projeto de portfólio para demonstrar backend Kotlin
aplicado a IA.

**🌐 API pública:** https://finrag-p3p5.onrender.com — comece pelo
[Swagger UI](https://finrag-p3p5.onrender.com/swagger-ui.html) (registre-se,
faça login e use o botão *Authorize*). Free tier: a primeira requisição após
~15 min de ociosidade pode levar **até 1 minuto** (cold start).

Arquitetura completa, diagrama e ADRs em [`specs/00-architecture.md`](specs/00-architecture.md).
O desenvolvimento segue spec-driven development: cada marco tem seus próprios
`requirements.md`/`design.md`/`tasks.md` em [`specs/`](specs/).

## Progresso

| Marco | Descrição | Status |
|-------|-----------|--------|
| M0 | Setup do projeto (Docker, CI, tooling) | ✅ Concluído |
| M1 | Autenticação (JWT) | ✅ Concluído |
| M2 | Ingestão de documentos (PDF/Markdown → chunking → embeddings) | ✅ Concluído |
| M3 | Q&A sobre documentos indexados (RAG) | ✅ Concluído |
| M4 | Observabilidade do pipeline RAG | ✅ Concluído |
| M5 | Gestão de documentos (GET/DELETE, paginação) | ✅ Concluído |
| M6 | Docs da API + hardening | ✅ Concluído |
| M7 | Deploy (Render + Neon, URL pública) | ✅ Concluído |
| M8 | Avaliação de RAG (golden dataset, calibração de retrieval) | ✅ Concluído |

Roadmap completo (motivação e ordem de cada marco futuro) em
[`specs/01-roadmap.md`](specs/01-roadmap.md).

## Stack

- Kotlin 2.x + Spring Boot 4.x, JDK 21 (toolchain), Gradle Kotlin DSL
- PostgreSQL + pgvector, migrations via Flyway
- Spring Security + JWT
- Kotest + Testcontainers para testes
- OpenAI `text-embedding-3-small` (embeddings) + Anthropic Claude Haiku (geração de resposta)

## Como rodar localmente

Requisitos: Docker e Docker Compose.

A ingestão de documentos (`POST /documents`) chama a API de embeddings da OpenAI
e as perguntas (`POST /questions`) chamam a API da Anthropic para gerar a
resposta, então defina as duas chaves antes de subir o ambiente:

```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
docker compose up
```

Isso sobe o Postgres com pgvector (serviço `db`) e a aplicação (serviço `app`,
buildada a partir do `Dockerfile` local). A aplicação fica disponível em
`http://localhost:8080`.

Verifique se subiu corretamente:

```bash
curl http://localhost:8080/actuator/health
```

## Documentação interativa (Swagger UI)

A API é autodocumentada via OpenAPI 3 (springdoc): a spec fica em
`http://localhost:8080/v3/api-docs` e a UI navegável em
`http://localhost:8080/swagger-ui.html` — ambas públicas.

Para testar pela UI: `POST /auth/register` → `POST /auth/login` → copie o
`accessToken` → botão **Authorize** → chame qualquer endpoint protegido dali
mesmo.

## Rate limiting

Os endpoints que chamam APIs pagas têm limite por usuário autenticado
(token bucket — rajadas curtas são aceitas, abuso sustentado não):

| Endpoint          | Limite padrão | Env vars                                                          |
|-------------------|---------------|--------------------------------------------------------------------|
| `POST /questions` | 10/min        | `RATE_LIMIT_QUESTIONS_CAPACITY` / `RATE_LIMIT_QUESTIONS_PERIOD_SECONDS` |
| `POST /documents` | 5/min         | `RATE_LIMIT_DOCUMENTS_CAPACITY` / `RATE_LIMIT_DOCUMENTS_PERIOD_SECONDS` |

Exceder o limite retorna `429 Too Many Requests` (`ProblemDetail`) com o header
`Retry-After` em segundos. Rejeições são contadas na métrica
`finrag.ratelimit.rejections`.

### Rodando sem Docker Compose

Para desenvolvimento com a aplicação rodando fora de container (ex.: via IDE),
suba só o banco:

```bash
docker run -d --name finrag-db -p 5432:5432 \
  -e POSTGRES_DB=finrag -e POSTGRES_USER=finrag -e POSTGRES_PASSWORD=finrag \
  pgvector/pgvector:pg16
```

Defina o segredo do JWT (obrigatório, sem valor padrão — precisa ter pelo
menos 32 bytes para o HS256) e as chaves da OpenAI e da Anthropic
(obrigatórias, também sem valor padrão) e rode a aplicação:

```bash
export JWT_SECRET=um-segredo-de-desenvolvimento-com-pelo-menos-32-bytes
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew bootRun
```

(o resto da config usa os valores padrão de `application.yaml`, que já
apontam para `localhost:5432`)

## Autenticação

```bash
# registro
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@email.com","password":"senha123"}'

# login — retorna { accessToken, tokenType, expiresIn }
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@email.com","password":"senha123"}'
```

Rotas autenticadas recebem o token no header `Authorization: Bearer <accessToken>`,
como nos exemplos de `/documents` abaixo.

## Documentos

Ingestão de documentos financeiros (PDF ou Markdown, até 10MB): o texto é
extraído, dividido em chunks e cada chunk vira um embedding
(`text-embedding-3-small`), tudo persistido de forma atômica.

```bash
# upload — retorna 201 com { id, filename, chunkCount, createdAt }
curl -X POST http://localhost:8080/documents \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@relatorio-trimestral.pdf"

# listagem paginada — só os documentos do usuário autenticado, mais recentes primeiro
# page (padrão 0) e size (padrão 20, máximo 100)
curl "http://localhost:8080/documents?page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"

# busca por id — retorna 200 com { id, filename, chunkCount, createdAt }
curl http://localhost:8080/documents/<id> \
  -H "Authorization: Bearer <accessToken>"

# remoção — retorna 204 e apaga também os chunks/embeddings do documento
curl -X DELETE http://localhost:8080/documents/<id> \
  -H "Authorization: Bearer <accessToken>"
```

A listagem responde no formato paginado:

```json
{
  "items": [ { "id": "…", "filename": "relatorio.pdf", "chunkCount": 12, "createdAt": "…" } ],
  "page": 0,
  "size": 20,
  "totalItems": 37,
  "totalPages": 2
}
```

Erros mapeados para `ProblemDetail`:

| Situação                                    | Status |
|----------------------------------------------|--------|
| Extensão não suportada (fora de `.pdf`/`.md`) | `415`  |
| Arquivo vazio ou sem texto extraível          | `422`  |
| Falha ao gerar embeddings (OpenAI)            | `502`  |
| Arquivo maior que o limite configurado        | `413`  |
| Documento inexistente ou de outro usuário     | `404`  |
| Parâmetro de paginação inválido               | `400`  |
| Limite de uploads por minuto excedido          | `429`  |
| Sem token / token inválido                    | `401`  |

## Perguntas

Q&A sobre os documentos já indexados (RAG): a pergunta vira embedding, os
chunks mais similares por cosseno são buscados no pgvector — só entre os
documentos do usuário autenticado —, e a resposta é gerada pela Anthropic
(Claude Haiku) com base apenas nesse contexto, citando as fontes usadas.

```bash
curl -X POST http://localhost:8080/questions \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"question":"Qual foi a receita no terceiro trimestre?"}'
```

Retorna `200` com `{ answer, sources: [{ documentId, filename, excerpt, similarity }] }`
— `excerpt` é um trecho de até 200 caracteres do chunk usado como fonte.
Chunks com similaridade abaixo do threshold (`finrag.rag.min-similarity`,
padrão `0.25`) são descartados; sem nenhum chunk relevante (corpus vazio ou
só documentos sobre outros assuntos), `answer` traz uma mensagem padrão
avisando disso e `sources` vem vazio — não é erro.

Erros mapeados para `ProblemDetail`:

| Situação                                      | Status |
|------------------------------------------------|--------|
| Pergunta vazia, ausente ou acima de 2000 chars  | `400`  |
| Falha ao gerar embedding da pergunta (OpenAI)   | `502`  |
| Falha ao gerar resposta (Anthropic)             | `502`  |
| Limite de perguntas por minuto excedido          | `429`  |
| Sem token / token inválido                      | `401`  |

## Avaliação de RAG

Como saber se a busca retorna os chunks certos? O retrieval é medido contra um
**golden dataset** (25 perguntas com fonte esperada conhecida, sobre um corpus
fictício versionado) por um harness que roda fora do build/CI:

```bash
OPENAI_API_KEY=sk-... ./gradlew ragEval   # ~US$ 0,0001 por rodada
```

Resultados da calibração (grid `topK × minSimilarity` computado em memória com
uma única rodada de embeddings):

- **recall@5 = 95%** e **MRR = 0,75** na configuração de produção
  (`topK=5`, `minSimilarity=0.25`) — `k=5` validado pelos dados: `k=3` perde
  9 p.p. de recall e `k=8` não ganha nada.
- **Nenhum threshold separa** "tem resposta" de "não tem": a similaridade dos
  acertos (0,46–0,76) se sobrepõe à dos chunks irrelevantes (0,55–0,71). A
  recusa é responsabilidade do prompt do RAG, não do threshold — medido, não
  suposto.

Metodologia completa, achados e limitações em [`docs/rag-eval.md`](docs/rag-eval.md).

## Observabilidade

Logs em formato estruturado JSON (ECS — `logging.structured.format.console: ecs`).
Nível de log padrão é `INFO`; para depuração local, ajuste sem recompilar:

```bash
export LOGGING_LEVEL_ROOT=DEBUG
```

Métricas do pipeline RAG expostas em formato Prometheus (público, sem
autenticação — mesmo padrão do `/actuator/health`):

```bash
curl http://localhost:8080/actuator/prometheus
```

Métricas customizadas:

| Métrica                        | Tipo    | Tags                              |
|---------------------------------|---------|------------------------------------|
| `finrag.pipeline.stage.duration` | Timer   | `pipeline=question\|ingestion`, `stage=embedding\|search\|llm\|extraction\|chunking` |
| `finrag.llm.tokens`             | Counter | `type=prompt\|completion`          |
| `finrag.provider.errors`        | Counter | `provider=openai\|anthropic`, `error_type=<classe da causa da falha>` |

## Deploy (produção)

A aplicação roda no **Render** (free tier, container buildado do `Dockerfile`
via Blueprint `render.yaml`) com Postgres + pgvector gerenciado no **Neon**
(free tier). Deploy contínuo: merge na `main` → CI (GitHub Actions) verde →
Render builda e publica (`autoDeployTrigger: checksPass`), com health check em
`/actuator/health` antes de rotear tráfego.

Limitações conscientes do free tier (custo mensal zero):

- **Cold start**: o serviço hiberna após 15 min sem tráfego e leva ~1 min para
  acordar na requisição seguinte.
- **Instância única** de 512MB (heap dimensionado via
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0`) — coerente com o rate
  limiting em memória.
- **Sem backup do banco** — os dados são recriáveis por re-ingestão.
- O compute do Neon suspende após ~5 min ocioso e retoma em ~1s (imperceptível).

**Rollback**: dashboard do Render → *Deploys* → *Rollback* no deploy anterior
(a imagem antiga é reutilizada, sem rebuild).

## Comandos úteis

- Build + testes: `./gradlew build`
- Só testes: `./gradlew test` (usa Testcontainers — requer Docker rodando)
- Avaliação de retrieval do RAG: `OPENAI_API_KEY=... ./gradlew ragEval` (API real da OpenAI)
- Health check: `GET /actuator/health`
- Métricas: `GET /actuator/prometheus`
- Collection do Postman com todos os endpoints: `postman/FinRAG.postman_collection.json`

# FinRAG

[![CI](https://github.com/eloizaams/finrag/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/eloizaams/finrag/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Coverage](https://img.shields.io/badge/coverage-97.6%25-brightgreen)](docs/rag-eval.md)

[🇧🇷 Português](README.md) · 🇺🇸 English (this file)

REST API in Kotlin + Spring Boot with a RAG (Retrieval-Augmented Generation)
pipeline over financial documents. Portfolio project demonstrating Kotlin
backend engineering applied to AI.

**🌐 Live API:** https://finrag-p3p5.onrender.com — start with the
[Swagger UI](https://finrag-p3p5.onrender.com/swagger-ui.html) (register, log
in, and use the *Authorize* button). Free tier: the first request after
~15 min of inactivity can take **up to 1 minute** (cold start).

Full architecture, diagram, and ADRs in [`specs/00-architecture.md`](specs/00-architecture.md).
Development follows spec-driven development: each milestone has its own
`requirements.md`/`design.md`/`tasks.md` in [`specs/`](specs/) (specs and
commit history are in Portuguese — the primary language of this project's
development log — but the code, README, and docs referenced below are
available in English).

## Highlights for recruiters

What this project demonstrates, in 30 seconds:

- **Hand-built RAG pipeline** (no Spring AI/LangChain4j): text extraction,
  chunking, embeddings, vector search, and prompt construction are all custom
  code — every stage has a decision recorded as an ADR and is defensible in
  an interview.
- **Quality measured, not assumed**: a 25-case golden dataset and an
  evaluation harness (`./gradlew ragEval`, ~US$ 0.0001/run) → **recall@5 = 95%**
  and **MRR = 0.75**; `topK=5` and `minSimilarity=0.25` were chosen from a
  calibration grid, not guessed ([`docs/rag-eval.md`](docs/rag-eval.md)).
- **A real engineering finding**: no similarity threshold cleanly separates
  "has an answer" from "doesn't" (correct matches score 0.46–0.76 vs.
  irrelevant chunks at 0.55–0.71) — so refusal is handled by the LLM prompt,
  not the threshold, a decision made with data.
- **In production at zero monthly cost**: Render + Neon (free tiers),
  continuous deployment (merge to `main` → green CI → publish with health
  check), a public URL testable via Swagger.
- **Complete backend engineering**: Clean Architecture, JWT, per-user rate
  limiting, Flyway migrations, integration tests with Testcontainers (real
  Postgres + pgvector, no database mocks), and observability (JSON/ECS logs +
  Prometheus metrics per pipeline stage).

Want to see it working? A 5-minute demo script is in [`docs/demo.md`](docs/demo.md).
Want to challenge the decisions? Every ADR is defended in [`docs/adr-defesa.md`](docs/adr-defesa.md)
(Portuguese — interview prep for the author).

## Progress

| Milestone | Description | Status |
|-----------|--------------|--------|
| M0 | Project setup (Docker, CI, tooling) | ✅ Done |
| M1 | Authentication (JWT) | ✅ Done |
| M2 | Document ingestion (PDF/Markdown → chunking → embeddings) | ✅ Done |
| M3 | Q&A over indexed documents (RAG) | ✅ Done |
| M4 | RAG pipeline observability | ✅ Done |
| M5 | Document management (GET/DELETE, pagination) | ✅ Done |
| M6 | API docs + hardening | ✅ Done |
| M7 | Deploy (Render + Neon, public URL) | ✅ Done |
| M8 | RAG evaluation (golden dataset, retrieval calibration) | ✅ Done |

Full roadmap (motivation and ordering for future milestones) in
[`specs/01-roadmap.md`](specs/01-roadmap.md) (Portuguese).

## Stack

- Kotlin 2.x + Spring Boot 4.x, JDK 21 (toolchain), Gradle Kotlin DSL
- PostgreSQL + pgvector, migrations via Flyway
- Spring Security + JWT
- Kotest + Testcontainers for testing
- OpenAI `text-embedding-3-small` (embeddings) + Anthropic Claude Haiku (answer generation)

## Running locally

Requirements: Docker and Docker Compose.

Document ingestion (`POST /documents`) calls the OpenAI embeddings API and
questions (`POST /questions`) call the Anthropic API to generate the answer,
so set both keys before bringing up the environment:

```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
docker compose up
```

This starts Postgres with pgvector (the `db` service) and the application
(the `app` service, built from the local `Dockerfile`). The app is available
at `http://localhost:8080`.

Check that it came up correctly:

```bash
curl http://localhost:8080/actuator/health
```

## Interactive documentation (Swagger UI)

The API is self-documented via OpenAPI 3 (springdoc): the spec is at
`http://localhost:8080/v3/api-docs` and the browsable UI at
`http://localhost:8080/swagger-ui.html` — both public.

To try it through the UI: `POST /auth/register` → `POST /auth/login` → copy
the `accessToken` → **Authorize** button → call any protected endpoint from
there.

## Rate limiting

Endpoints that call paid APIs are limited per authenticated user (token
bucket — short bursts are allowed, sustained abuse isn't):

| Endpoint          | Default limit | Env vars                                                          |
|-------------------|---------------|--------------------------------------------------------------------|
| `POST /questions` | 10/min        | `RATE_LIMIT_QUESTIONS_CAPACITY` / `RATE_LIMIT_QUESTIONS_PERIOD_SECONDS` |
| `POST /documents` | 5/min         | `RATE_LIMIT_DOCUMENTS_CAPACITY` / `RATE_LIMIT_DOCUMENTS_PERIOD_SECONDS` |

Exceeding the limit returns `429 Too Many Requests` (`ProblemDetail`) with a
`Retry-After` header in seconds. Rejections are counted in the
`finrag.ratelimit.rejections` metric.

### Running without Docker Compose

For development with the application running outside a container (e.g. from
an IDE), bring up just the database:

```bash
docker run -d --name finrag-db -p 5432:5432 \
  -e POSTGRES_DB=finrag -e POSTGRES_USER=finrag -e POSTGRES_PASSWORD=finrag \
  pgvector/pgvector:pg16
```

Set the JWT secret (required, no default — needs at least 32 bytes for
HS256) and the OpenAI/Anthropic keys (also required, no defaults), then run
the app:

```bash
export JWT_SECRET=a-dev-secret-with-at-least-32-bytes
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew bootRun
```

(everything else falls back to the defaults in `application.yaml`, which
already point at `localhost:5432`)

## Authentication

```bash
# register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@email.com","password":"senha123"}'

# login — returns { accessToken, tokenType, expiresIn }
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@email.com","password":"senha123"}'
```

Authenticated routes take the token in the `Authorization: Bearer <accessToken>`
header, as in the `/documents` examples below.

## Documents

Ingestion of financial documents (PDF or Markdown, up to 10MB): text is
extracted, split into chunks, and each chunk becomes an embedding
(`text-embedding-3-small`), all persisted atomically.

```bash
# upload — returns 201 with { id, filename, chunkCount, createdAt }
curl -X POST http://localhost:8080/documents \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@quarterly-report.pdf"

# paginated listing — only the authenticated user's documents, newest first
# page (default 0) and size (default 20, max 100)
curl "http://localhost:8080/documents?page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"

# fetch by id — returns 200 with { id, filename, chunkCount, createdAt }
curl http://localhost:8080/documents/<id> \
  -H "Authorization: Bearer <accessToken>"

# delete — returns 204 and also removes the document's chunks/embeddings
curl -X DELETE http://localhost:8080/documents/<id> \
  -H "Authorization: Bearer <accessToken>"
```

The listing responds in a paginated shape:

```json
{
  "items": [ { "id": "…", "filename": "report.pdf", "chunkCount": 12, "createdAt": "…" } ],
  "page": 0,
  "size": 20,
  "totalItems": 37,
  "totalPages": 2
}
```

Errors mapped to `ProblemDetail`:

| Situation                                    | Status |
|------------------------------------------------|--------|
| Unsupported extension (other than `.pdf`/`.md`) | `415`  |
| Empty file or no extractable text               | `422`  |
| Failed to generate embeddings (OpenAI)          | `502`  |
| File larger than the configured limit           | `413`  |
| Document doesn't exist or belongs to another user | `404`  |
| Invalid pagination parameter                    | `400`  |
| Upload rate limit exceeded                      | `429`  |
| Missing / invalid token                         | `401`  |

## Questions

Q&A over already-indexed documents (RAG): the question becomes an embedding,
the most similar chunks by cosine similarity are looked up in pgvector — only
among the authenticated user's documents — and the answer is generated by
Anthropic (Claude Haiku) based solely on that context, citing the sources
used.

```bash
curl -X POST http://localhost:8080/questions \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"question":"What was revenue in Q3?"}'
```

Returns `200` with `{ answer, sources: [{ documentId, filename, excerpt, similarity }] }`
— `excerpt` is a snippet of up to 200 characters from the chunk used as a
source. Chunks with similarity below the threshold (`finrag.rag.min-similarity`,
default `0.25`) are discarded; with no relevant chunk (empty corpus, or
documents only about other subjects), `answer` carries a default message
saying so and `sources` comes back empty — this is not an error.

Errors mapped to `ProblemDetail`:

| Situation                                       | Status |
|---------------------------------------------------|--------|
| Empty, missing, or over-2000-char question         | `400`  |
| Failed to generate the question's embedding (OpenAI) | `502`  |
| Failed to generate the answer (Anthropic)          | `502`  |
| Question rate limit exceeded                       | `429`  |
| Missing / invalid token                            | `401`  |

## RAG evaluation

How do we know retrieval returns the right chunks? Retrieval is measured
against a **golden dataset** (25 questions with a known expected source, over
a versioned fictional corpus) by a harness that runs outside the build/CI:

```bash
OPENAI_API_KEY=sk-... ./gradlew ragEval   # ~US$ 0.0001 per run
```

Calibration results (`topK × minSimilarity` grid, computed in memory from a
single embeddings run):

- **recall@5 = 95%** and **MRR = 0.75** at the production configuration
  (`topK=5`, `minSimilarity=0.25`) — `k=5` validated by the data: `k=3` loses
  9 points of recall and `k=8` gains nothing.
- **No threshold cleanly separates** "has an answer" from "doesn't": the
  similarity of correct matches (0.46–0.76) overlaps with that of irrelevant
  chunks (0.55–0.71). Refusal is the RAG prompt's responsibility, not the
  threshold's — measured, not assumed.

Full methodology, findings, and limitations in [`docs/rag-eval.md`](docs/rag-eval.md)
(Portuguese).

## Observability

Structured JSON logs (ECS — `logging.structured.format.console: ecs`).
Default log level is `INFO`; for local debugging, adjust without recompiling:

```bash
export LOGGING_LEVEL_ROOT=DEBUG
```

RAG pipeline metrics exposed in Prometheus format (public, no authentication
— same pattern as `/actuator/health`):

```bash
curl http://localhost:8080/actuator/prometheus
```

Custom metrics:

| Metric                          | Type    | Tags                              |
|-----------------------------------|---------|------------------------------------|
| `finrag.pipeline.stage.duration` | Timer   | `pipeline=question\|ingestion`, `stage=embedding\|search\|llm\|extraction\|chunking` |
| `finrag.llm.tokens`             | Counter | `type=prompt\|completion`          |
| `finrag.provider.errors`        | Counter | `provider=openai\|anthropic`, `error_type=<failure cause class>` |

## Deployment (production)

The application runs on **Render** (free tier, container built from the
`Dockerfile` via the `render.yaml` Blueprint) with managed Postgres + pgvector
on **Neon** (free tier). Continuous deployment: merge to `main` → green CI
(GitHub Actions) → Render builds and publishes (`autoDeployTrigger: checksPass`),
with a health check at `/actuator/health` before routing traffic.

Conscious free-tier limitations (zero monthly cost):

- **Cold start**: the service hibernates after 15 min without traffic and
  takes ~1 min to wake up on the next request.
- **Single 512MB instance** (heap sized via
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0`) — consistent with the
  in-memory rate limiting.
- **No database backups** — data is recreatable via re-ingestion.
- Neon's compute suspends after ~5 min idle and resumes in ~1s (unnoticeable).

**Rollback**: Render dashboard → *Deploys* → *Rollback* to the previous
deploy (the old image is reused, no rebuild).

## Useful commands

- Build + tests: `./gradlew build`
- Tests only: `./gradlew test` (uses Testcontainers — requires Docker running)
- Test coverage (Kover): `./gradlew koverHtmlReport` → report at
  `build/reports/kover/html/index.html` (97.6% line coverage as of the last
  measurement; `ragEval` is excluded since it calls the real OpenAI API)
- RAG retrieval evaluation: `OPENAI_API_KEY=... ./gradlew ragEval` (real OpenAI API)
- Health check: `GET /actuator/health`
- Metrics: `GET /actuator/prometheus`
- Postman collection with all endpoints: `postman/FinRAG.postman_collection.json`
- 5-minute demo script (with ready-made documents and questions): [`docs/demo.md`](docs/demo.md)
  (Portuguese)

## License

[MIT](LICENSE)

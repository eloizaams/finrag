# FinRAG

API REST em Kotlin + Spring Boot com um pipeline RAG (Retrieval-Augmented Generation)
sobre documentos financeiros. Projeto de portfólio para demonstrar backend Kotlin
aplicado a IA.

Arquitetura completa, diagrama e ADRs em [`specs/00-architecture.md`](specs/00-architecture.md).
O desenvolvimento segue spec-driven development: cada marco tem seus próprios
`requirements.md`/`design.md`/`tasks.md` em [`specs/`](specs/).

## Stack

- Kotlin 2.x + Spring Boot 4.x, JDK 21 (toolchain), Gradle Kotlin DSL
- PostgreSQL + pgvector, migrations via Flyway
- Spring Security + JWT
- Kotest + Testcontainers para testes
- OpenAI `text-embedding-3-small` (embeddings) + Anthropic Claude Haiku (geração de resposta)

## Como rodar localmente

Requisitos: Docker e Docker Compose.

```bash
docker compose up
```

Isso sobe o Postgres com pgvector (serviço `db`) e a aplicação (serviço `app`,
buildada a partir do `Dockerfile` local). A aplicação fica disponível em
`http://localhost:8080`.

Verifique se subiu corretamente:

```bash
curl http://localhost:8080/actuator/health
```

### Rodando sem Docker Compose

Para desenvolvimento com a aplicação rodando fora de container (ex.: via IDE),
suba só o banco:

```bash
docker run -d --name finrag-db -p 5432:5432 \
  -e POSTGRES_DB=finrag -e POSTGRES_USER=finrag -e POSTGRES_PASSWORD=finrag \
  pgvector/pgvector:pg16
```

Defina o segredo do JWT (obrigatório, sem valor padrão — precisa ter pelo
menos 32 bytes para o HS256) e rode a aplicação:

```bash
export JWT_SECRET=um-segredo-de-desenvolvimento-com-pelo-menos-32-bytes
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

# rota autenticada: envie o token no header Authorization
curl http://localhost:8080/algum-endpoint-protegido \
  -H "Authorization: Bearer <accessToken>"
```

## Comandos úteis

- Build + testes: `./gradlew build`
- Só testes: `./gradlew test` (usa Testcontainers — requer Docker rodando)
- Health check: `GET /actuator/health`

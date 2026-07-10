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

# listagem — só os documentos do usuário autenticado
curl http://localhost:8080/documents \
  -H "Authorization: Bearer <accessToken>"
```

Erros mapeados para `ProblemDetail`:

| Situação                                    | Status |
|----------------------------------------------|--------|
| Extensão não suportada (fora de `.pdf`/`.md`) | `415`  |
| Arquivo vazio ou sem texto extraível          | `422`  |
| Falha ao gerar embeddings (OpenAI)            | `502`  |
| Arquivo maior que o limite configurado        | `413`  |
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
| Pergunta vazia ou ausente                       | `400`  |
| Falha ao gerar embedding da pergunta (OpenAI)   | `502`  |
| Falha ao gerar resposta (Anthropic)             | `502`  |
| Sem token / token inválido                      | `401`  |

## Comandos úteis

- Build + testes: `./gradlew build`
- Só testes: `./gradlew test` (usa Testcontainers — requer Docker rodando)
- Health check: `GET /actuator/health`

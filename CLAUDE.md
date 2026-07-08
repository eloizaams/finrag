# FinRAG — instruções para Claude Code

API REST em Kotlin + Spring Boot com pipeline RAG sobre documentos financeiros.
Projeto de portfólio — arquitetura completa, diagrama e ADRs em `specs/00-architecture.md`.

## Fluxo de trabalho (spec-driven)

- Cada marco (M0, M1, ...) tem três arquivos em `specs/`: `MX-requirements.md` (critério
  de aceite), `MX-design.md` (decisões técnicas) e `MX-tasks.md` (checklist executável).
- Antes de implementar qualquer marco, leia os três arquivos correspondentes.
- A autora escreve o código com orientação: não gere a implementação inteira de um marco
  de uma vez. Explique o conceito primeiro (principalmente os de IA — embeddings, chunking,
  busca semântica, prompt de RAG), proponha a abordagem, e implemente em passos pequenos
  e revisáveis, para que ela consiga defender cada decisão em entrevista.
- Ao concluir uma tarefa, marque o checkbox correspondente em `specs/MX-tasks.md` e sugira
  o commit semântico.

## Stack

- Kotlin 2.x + Spring Boot 4.x, JDK 21 (toolchain), Gradle Kotlin DSL
- PostgreSQL + pgvector, migrations via Flyway
- Spring Security + JWT
- Kotest + Testcontainers para testes
- OpenAI `text-embedding-3-small` (embeddings) + Anthropic Claude Haiku (geração de resposta)

## Comandos

- Build + testes: `./gradlew build`
- Só testes: `./gradlew test`
- Subir ambiente local: `docker compose up`
- Health check: `GET /actuator/health`

## Convenções

- Commits semânticos (`feat:`, `fix:`, `chore:`, `test:`, `docs:`, `refactor:`)
- Clean Architecture pragmática: `domain` não depende de nada; `application` depende só
  de `domain`; `infrastructure`/`api` dependem de `domain`/`application`, nunca o contrário
- Sem `ddl-auto: update` — todo schema via migration Flyway
- Testes de integração usam Testcontainers (Postgres real com pgvector), não mocks de banco
- Chaves de API sempre via variável de ambiente, nunca hardcoded ou commitadas

## Fluxo Git (git flow + Conventional Commits)

- `main`: sempre deployável, nunca commit direto
- `develop`: branch de integração, base de trabalho padrão
- `feature/<slug>`: uma por marco/tarefa (ex.: `feature/m1-auth-jwt`), sai de `develop`,
  volta para `develop`
- `release/<versao>`: preparação de release antes do merge em `main` (opcional, ao
  fechar um marco maior)
- `hotfix/<slug>`: correção urgente, sai de `main`, volta para `main` e `develop`
- Merge em `develop`/`main` sempre via PR, mesmo trabalhando sozinha — serve de
  histórico revisável
- Mensagens de commit em Conventional Commits: `<tipo>(<escopo opcional>): <descrição>`
  — tipos: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `style`, `perf`, `ci`, `build`
- Ao concluir um marco com CI verde na `develop`, merge para `main` e tag `vX.Y.Z`
- Use a skill `commit` (`.claude/skills/commit/SKILL.md`) para gerar commits nesse padrão

## Fora de escopo na v1 (não implementar sem alinhar antes)

Multi-tenancy real, re-ranking de busca, streaming de resposta (SSE), formatos além de
PDF/Markdown, ingestão assíncrona via fila. Candidatos a marco opcional M9.

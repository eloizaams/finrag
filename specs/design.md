# M0 — Setup — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Build tool | Gradle (Kotlin DSL) | Padrão de mercado em projetos Kotlin/Spring; melhor suporte no ecossistema Spring Boot 3.x |
| JDK | 21 (LTS) | Versão LTS mais recente com suporte a virtual threads, relevante para I/O-bound (chamadas a LLM/embeddings) |
| Migrations | Flyway | Mais simples que Liquibase para projeto pequeno; SQL puro facilita revisão em PR |
| Imagem base | `eclipse-temurin:21-jre-alpine` (runtime) + multi-stage build | Imagem final pequena; build stage separado com JDK completo |
| Banco (local/dev) | `pgvector/pgvector:pg16` (imagem oficial) | Já vem com a extensão compilada, evita instalar manualmente |
| Observabilidade base | Spring Boot Actuator | Necessário desde já para healthcheck do Docker Compose |

## Estrutura de dependências (build.gradle.kts) — núcleo do M0

- `spring-boot-starter-web`
- `spring-boot-starter-actuator`
- `spring-boot-starter-data-jpa`
- `flyway-core` + `flyway-database-postgresql`
- `postgresql` (driver JDBC)
- `kotest-runner-junit5` + `kotest-assertions-core` (test)
- `testcontainers` + `testcontainers-postgresql` (test)

> Dependências de segurança (JWT), OpenAI e Anthropic entram nos marcos seguintes,
> não aqui — mantém o M0 focado em infraestrutura.

## docker-compose.yml — esboço conceitual

Dois serviços:
- `db`: imagem `pgvector/pgvector:pg16`, variáveis de ambiente para usuário/senha/db,
  volume nomeado para persistência, healthcheck via `pg_isready`
- `app`: build a partir do `Dockerfile` local, depende de `db` com `condition: service_healthy`,
  variáveis de ambiente apontando para o `db` pelo nome do serviço (network interna do compose)

## Migration inicial (Flyway V1)

Habilita a extensão pgvector no banco:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Isso é tudo que o M0 precisa em termos de schema — as tabelas de domínio
(`documents`, `chunks`, `users`) entram nos marcos M1/M2 com suas próprias migrations.

## CI (GitHub Actions) — esboço conceitual

Trigger: `push` e `pull_request` na branch `main`.
Steps: checkout → setup JDK 21 → cache Gradle → `./gradlew build` (isso já roda os
testes, incluindo os de Testcontainers, que sobem Postgres via Docker-in-Docker
no runner).

## Trade-offs conscientes

- **Testcontainers no CI** aumenta o tempo de build (~1-2min a mais por subir container),
  mas garante que os testes de integração rodem contra um Postgres real com pgvector —
  troca aceitável para confiabilidade.
- **Flyway vs JPA `ddl-auto`**: optamos por Flyway desde o M0 mesmo sendo "mais setup",
  porque `ddl-auto: update` é anti-pattern reconhecido e pgvector como tipo de coluna
  custom precisa de SQL explícito de qualquer forma.

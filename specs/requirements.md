# M0 — Setup — Requirements

## Objetivo do marco

Ter um projeto Spring Boot rodando localmente via Docker Compose, com banco
PostgreSQL + extensão pgvector disponível, e um pipeline de CI que builda e
roda testes a cada push/PR.

## Critérios de aceite

1. `docker-compose up` sobe a aplicação e o Postgres sem erro
2. Extensão `pgvector` está habilitada no banco (`CREATE EXTENSION IF NOT EXISTS vector`)
3. Endpoint `GET /actuator/health` responde `200 OK` com status `UP`, incluindo
   o healthcheck do banco de dados
4. Projeto builda com `./gradlew build` sem warnings de configuração
5. Workflow do GitHub Actions roda em push/PR para `main`: build + testes
6. README raiz do projeto contém: como rodar localmente, stack usada, link para `specs/`

## Fora de escopo neste marco

- Qualquer endpoint de negócio (auth, ingestão, perguntas)
- Configuração de ambiente de produção/deploy
- Migrations complexas (Flyway/Liquibase entram aqui, mas só com a extensão vector)

## Perguntas em aberto (decidir no design.md)

- Gradle Kotlin DSL ou Maven?
- Flyway ou Liquibase para migrations?
- Qual imagem base Docker para a aplicação (JDK 21 slim)?

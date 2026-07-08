# M0 — Setup — Tasks

- [x] Inicializar projeto Spring Boot (Spring Initializr: Kotlin, Gradle Kotlin DSL, JDK 21)
- [x] Adicionar dependências: web, actuator, data-jpa, flyway, postgresql driver
- [x] Adicionar dependências de teste: kotest, testcontainers
- [x] Criar `Dockerfile` multi-stage (build + runtime)
- [x] Criar `docker-compose.yml` com serviços `db` (pgvector/pgvector:pg16) e `app`
- [x] Criar migration Flyway `V1__enable_pgvector.sql`
- [x] Configurar `application.yml` (dev) apontando para o serviço `db`
- [x] Validar `docker-compose up` sobe tudo e `/actuator/health` responde `UP`
- [x] Escrever primeiro teste com Testcontainers validando conexão + extensão vector habilitada
- [x] Criar `.github/workflows/ci.yml` com build + testes
- [ ] Validar CI verde em um PR de teste
- [x] Escrever README raiz (stack, como rodar, link para `specs/`)
- [ ] Commit semântico final do marco (`chore: setup inicial do projeto (M0)`)

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `requirements.md` atendidos + CI verde na main.

# M0 — Setup — Tasks

- [ ] Inicializar projeto Spring Boot (Spring Initializr: Kotlin, Gradle Kotlin DSL, JDK 21)
- [ ] Adicionar dependências: web, actuator, data-jpa, flyway, postgresql driver
- [ ] Adicionar dependências de teste: kotest, testcontainers
- [ ] Criar `Dockerfile` multi-stage (build + runtime)
- [ ] Criar `docker-compose.yml` com serviços `db` (pgvector/pgvector:pg16) e `app`
- [ ] Criar migration Flyway `V1__enable_pgvector.sql`
- [ ] Configurar `application.yml` (dev) apontando para o serviço `db`
- [ ] Validar `docker-compose up` sobe tudo e `/actuator/health` responde `UP`
- [ ] Escrever primeiro teste com Testcontainers validando conexão + extensão vector habilitada
- [ ] Criar `.github/workflows/ci.yml` com build + testes
- [ ] Validar CI verde em um PR de teste
- [ ] Escrever README raiz (stack, como rodar, link para `specs/`)
- [ ] Commit semântico final do marco (`chore: setup inicial do projeto (M0)`)

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `requirements.md` atendidos + CI verde na main.

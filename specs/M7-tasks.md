# M7 — Deploy — Tasks

## Preparação no repositório

- [x] Dimensionar a JVM para os 512MB da instância free:
      `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0` via `envVars` do
      render.yaml (ENTRYPOINT genérico — ajustado após code review)
- [x] Bindar na porta injetada pelo Render: `server.port: ${PORT:8080}` no
      `application.yaml` (ajustado após code review)
- [x] Criar `render.yaml` (Blueprint): web service Docker no plano free,
      `healthCheckPath: /actuator/health`, `autoDeployTrigger: checksPass`,
      env vars com secrets marcados `sync: false`
- [x] Validar que a imagem Docker local sobe e responde com o novo
      `ENTRYPOINT` (`docker compose up` + health check)

## Banco (Neon) — passos manuais da autora

- [x] Criar projeto no Neon (região AWS us-east-1), free tier
- [x] Confirmar `CREATE EXTENSION vector` disponível (a migration V1 cuida da
      criação no primeiro boot)
- [x] Guardar connection string JDBC (`?sslmode=require`) + usuário/senha

## Plataforma (Render) — passos manuais da autora

- [x] Criar o serviço via Blueprint (`render.yaml`) apontando para o repo,
      branch `main`, plano Free
- [x] Preencher secrets no dashboard: `SPRING_DATASOURCE_URL`/`USERNAME`/
      `PASSWORD` (Neon), `JWT_SECRET` **novo de produção** (32+ bytes),
      `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`
- [x] Configurar auto-deploy "After CI Checks Pass" na `main`

## Release e CD

- [x] Abrir PR `feature/m7-deploy → develop` com os arquivos de deploy e docs
- [x] Após merge: abrir o PR de release `develop → main` (primeiro release)
- [x] Confirmar que o merge na `main` disparou o deploy no Render após o CI
      verde

## Validação pública (smoke test)

- [x] `GET /actuator/health` → `UP` na URL pública (HTTPS)
- [x] Swagger UI acessível na URL pública
- [x] Fluxo completo com APIs reais: register → login → authorize → upload →
      pergunta com resposta e fontes
- [x] Rate limiting ativo (estourar o limite de perguntas → `429` +
      `Retry-After`)
- [x] Migrations V1–V5 aplicadas no Neon (tabelas e extensão `vector`
      presentes)
- [x] Rollback testado ou procedimento verificado no dashboard do Render

## Fechamento do marco

- [x] Atualizar README: URL pública, o que testar, limitações do free tier
      (cold start ~1 min, instância única, sem backup) e procedimento de
      rollback
- [x] Marcar progresso em `00-architecture.md`/`01-roadmap.md`
- [x] Commit(s) semânticos ao longo do marco
- [x] Rodar `./gradlew build` limpo antes de cada PR

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M7-requirements.md` atendidos — em especial o
fluxo completo funcionando na URL pública com custo mensal zero — e o fluxo de
release `develop → main → deploy` demonstrado de ponta a ponta.

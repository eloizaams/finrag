# M7 — Deploy — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Plataforma da app | **Render** (Web Service free, runtime Docker) | Único free tier real entre os candidatos em 2026: 750h de instância/mês (cobre 1 serviço 24/7), builda o `Dockerfile` do repo, HTTPS e auto-deploy nativos, sem cartão. Fly.io aposentou o free tier (pay-as-you-go com cartão); alternativas menores não têm vantagem |
| Banco | **Neon** (free tier) | pgvector nativo, 0,5GB, 100 CU-horas/mês e **sem expiração** — o Postgres free do próprio Render expira em 30 dias e apaga os dados; o Supabase free pausa após 1 semana ocioso exigindo despausa manual. O scale-to-zero do Neon retoma em ~1s, imperceptível |
| Conexão com o Neon | Env vars `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` no Render | O Spring sobrepõe o `application.yaml` por env var (relaxed binding) — a connection string do Neon (com `?sslmode=require`) entra **sem mudança de código**. As vars `DB_*` continuam para o ambiente local |
| CD | Auto-deploy nativo do Render na `main`, com **"After CI Checks Pass"** | Merge na `main` só vira deploy depois do GitHub Actions verde — o gate de testes continua sendo o CI existente, sem pipeline novo para manter |
| Infra como config | `render.yaml` (Blueprint) versionado no repo | Declara serviço, runtime Docker, health check e env vars (secrets com `sync: false`) — reproduzível e visível no portfólio, sem precisar de Terraform |
| Build | Render builda direto do `Dockerfile` | O multi-stage do M0 já produz imagem enxuta (JRE alpine, non-root). Publicar em registry (GHCR) só adicionaria uma etapa sem ganho aqui |
| Memória da JVM | `-XX:MaxRAMPercentage=75.0` no `ENTRYPOINT` | A instância free tem 512MB; o default container-aware da JVM limita o heap a 25% (128MB) — apertado para Spring Boot. 75% (~384MB) deixa folga para metaspace/threads sem estourar o container |
| Health check | `/actuator/health` como `healthCheckPath` do Render | Já público desde o M0; o Render o usa para considerar o deploy saudável antes de rotear tráfego |
| Primeiro release | PR direto `develop → main` | Branch `release/1.0` não agrega em projeto solo com CI obrigatória — o fluxo do CLAUDE.md a trata como opcional. Releases seguintes repetem o mesmo PR quando houver algo a publicar |
| Cold start | Aceitar e documentar no README | Spin-down após 15 min ociosos, ~1 min para acordar. Nota honesta gerencia a expectativa de quem testa; ping externo para manter quente contraria o espírito do free tier e adiciona dependência frágil |
| Rollback | Rollback nativo do Render (dashboard → deploy anterior) | Um clique, sem tooling novo; documentado no README como procedimento |
| Região | Virginia (US East) | A mais próxima do Brasil entre as regiões do Render; Neon criado na região equivalente (AWS us-east-1) para latência mínima app↔banco |

## O que muda no repositório

```
Dockerfile          # (alterado) ENTRYPOINT com -XX:MaxRAMPercentage=75.0
render.yaml         # (novo) Blueprint: web service Docker, plano free,
                    #   healthCheckPath /actuator/health, autoDeploy on,
                    #   env vars (secrets com sync: false)
README.md           # (alterado) seção "Deploy / URL pública": endereço, o que
                    #   testar, limitações do free tier, rollback
```

Nenhuma mudança em `src/` — o marco é de infraestrutura/processo.

## Runbook do primeiro deploy (passos manuais, executados pela autora)

1. **Neon**: criar projeto (região AWS us-east-1), copiar a connection string
   (`postgresql://user:senha@host/db?sslmode=require`) e convertê-la para JDBC
   (`jdbc:postgresql://host/db?sslmode=require` + user/senha separados).
   A migration `V1` roda `CREATE EXTENSION IF NOT EXISTS vector` — suportado
   no free tier do Neon.
2. **Render**: criar Web Service a partir do repositório GitHub (New →
   Blueprint, apontando para o `render.yaml`), plano Free, branch `main`.
3. Preencher os secrets no dashboard: `SPRING_DATASOURCE_URL`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`
   (gerar valor novo de produção com 32+ bytes — **não** reutilizar o de dev),
   `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`.
4. Configurar auto-deploy como "After CI Checks Pass".
5. Merge do PR `develop → main` dispara o primeiro deploy.
6. Smoke test público (script de validação do fechamento).

## Fluxo de release (a partir deste marco)

1. Feature branches → PR → `develop` (como sempre, CI verde)
2. Quando houver algo a publicar: PR `develop → main`
3. CI verde na `main` → Render builda o `Dockerfile` e faz deploy com health
   check antes de rotear tráfego
4. Problema em produção → rollback pelo dashboard do Render (deploy anterior)

## Migration Flyway

Nenhuma — as existentes (V1–V5) rodam sozinhas no primeiro boot contra o Neon.

## Dependências novas (build.gradle.kts)

Nenhuma.

## Configuração (application.yaml)

Nenhuma mudança — produção usa env vars por cima do que já existe.

## Trade-offs conscientes

- **Cold start de ~1 min** após 15 min ociosos: documentado, não mitigado.
  Aceitável para portfólio; a alternativa (ping) é frágil e antiética com o
  free tier.
- **Instância única de 512MB**: coerente com o rate limiting em memória do M6
  (que pressupõe 1 instância). Sem HA — indisponibilidade durante deploy é
  mitigada pelo health check do Render, não eliminada.
- **Free tier do Neon suspende o compute após 5 min ociosos**: a primeira
  query paga ~1s de resume — irrelevante perto do cold start da app.
- **Sem backup do banco**: free tier. O dado do portfólio é recriável
  (re-ingestão); documentado como limitação.
- **Segredos direto no dashboard do Render** (não em um cofre): proporcional
  ao escopo; um secret manager seria over-engineering aqui.

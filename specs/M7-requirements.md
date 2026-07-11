# M7 — Deploy — Requirements

## Objetivo do marco

Tornar o portfólio acessível por URL pública: a aplicação containerizada
rodando em free tier, com Postgres + pgvector gerenciado, deploy contínuo a
partir da `main` (git flow: `main` = deployável, hoje ainda parada no setup
inicial) e documentação de acesso no README. Só é viável agora porque o M6
colocou rate limiting na frente das APIs pagas — expor a API sem isso seria
expor custo.

## Critérios de aceite

1. A aplicação responde em URL pública com HTTPS; `GET /actuator/health`
   retorna `UP`.
2. O fluxo completo funciona no ambiente público com as APIs reais: Swagger UI
   acessível → register → login → authorize → upload de documento → pergunta
   com resposta e fontes.
3. Postgres com pgvector gerenciado em free tier **sem expiração**; migrations
   Flyway aplicadas automaticamente no deploy (como já ocorre no boot).
4. Todos os segredos (`JWT_SECRET`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`,
   credenciais do banco) configurados como secrets da plataforma — nada
   commitado, mesmo critério de sempre.
5. Deploy contínuo: merge na `main` dispara build e deploy automáticos; o CI
   de build+testes continua obrigatório antes.
6. Primeiro release feito: `develop` promovida à `main` via PR (fechando o
   ciclo git flow do projeto).
7. Rate limiting ativo em produção com os limites padrão (10 perguntas/min,
   5 uploads/min).
8. README atualizado com a URL pública, o que dá para testar e as limitações
   do free tier (ex.: cold start).
9. Procedimento de rollback documentado (voltar à versão anterior em caso de
   deploy quebrado).
10. Custo mensal zero: tudo em free tier, com as limitações documentadas.

## Fora de escopo neste marco

- Domínio próprio / DNS customizado — a URL da plataforma basta
- Infra as Code (Terraform), Kubernetes
- Ambiente de staging separado — `develop` é validada por CI + Testcontainers
- Múltiplas instâncias / HA — o rate limit em memória pressupõe instância única (decisão do M6)
- Monitoramento externo e alertas — métricas seguem expostas no `/actuator/prometheus`
- Backup/restore automatizado do banco além do que a plataforma der de graça

## Perguntas em aberto (decidir no design.md)

- Plataforma da aplicação: Render, Fly.io ou outra? (estado **atual** dos free
  tiers precisa de pesquisa — mudam com frequência)
- Postgres gerenciado: Neon, Supabase ou o Postgres da própria plataforma?
  (requisitos: pgvector disponível + free tier que não expira)
- CD: workflow no GitHub Actions (deploy hook/CLI) ou auto-deploy nativo da
  plataforma conectado ao repositório?
- A plataforma builda a partir do `Dockerfile` ou publicamos imagem em registry
  (GHCR) e ela só executa?
- Primeiro release: PR direto `develop → main` ou branch `release/1.0`?
- Cold start do free tier: aceitar e documentar, ou mitigar (ping periódico)?

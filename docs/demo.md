# Roteiro de demo — 5 minutos

Demonstração ao vivo do FinRAG pela API pública, usando só o navegador
(Swagger UI). A mensagem central: **um pipeline RAG implementado à mão, em
produção, com qualidade medida** — os números do M8 fecham a história.

## Antes de começar (fora dos 5 minutos)

- [ ] **Acordar o serviço**: abrir https://finrag-p3p5.onrender.com/actuator/health
  uns 5 minutos antes — no free tier do Render, a primeira requisição após
  ~15 min de ociosidade leva até 1 minuto (cold start).
- [ ] Deixar aberta a aba do Swagger:
  https://finrag-p3p5.onrender.com/swagger-ui.html
- [ ] Ter à mão os documentos fictícios do corpus (são os mesmos do golden
  dataset do M8, em [`src/test/resources/golden/corpus/`](../src/test/resources/golden/corpus/)):
  - `relatorio-anual-acme.md` — vai ser enviado na demo
  - `faq-fundos.md` — reserva para o bônus (ver bloco final)
- [ ] Se preferir não criar usuário ao vivo, registrar antes e guardar
  email/senha (o token expira, mas o login é rápido).

## Roteiro

### 0:00–1:00 — Contexto + autenticação

Três frases de abertura:

> "FinRAG é uma API de perguntas e respostas sobre documentos financeiros,
> usando RAG: o documento vira chunks com embeddings no pgvector, e a pergunta
> busca os trechos mais similares antes de o LLM responder. É Kotlin + Spring
> Boot, com o pipeline implementado à mão — sem framework de RAG — justamente
> para eu poder defender cada etapa. Está em produção com custo mensal zero."

No Swagger: `POST /auth/register` → `POST /auth/login` → copiar o
`accessToken` → botão **Authorize**.

### 1:00–2:00 — Ingestão

`POST /documents` com `relatorio-anual-acme.md`. Mostrar a resposta `201` e
apontar o `chunkCount`:

> "O texto foi extraído, dividido em chunks por parágrafo com overlap, cada
> chunk virou um embedding do `text-embedding-3-small`, e tudo foi persistido
> numa transação só no Postgres com pgvector — uma das razões de eu ter
> escolhido pgvector em vez de um vector DB dedicado."

### 2:00–3:30 — Pergunta com resposta (o caminho feliz)

`POST /questions` com uma pergunta **parafraseada** (de propósito — não é
frase literal do documento, é busca semântica, não textual):

```json
{ "question": "Quanto a ACME faturou no consolidado e qual segmento puxou o crescimento?" }
```

Mostrar o `answer` (receita de R$ 4,2 bi, crescimento puxado pela logística) e
os `sources[]` com `similarity`:

> "A pergunta virou embedding, o pgvector devolveu os top-5 chunks por
> similaridade de cosseno — só entre os documentos deste usuário — e o Claude
> Haiku respondeu **apenas com base nesses trechos**, citando as fontes. Essa
> similaridade que aparece aqui é a mesma métrica que calibrei no M8."

### 3:30–4:15 — Pergunta sem resposta (o momento "eu medi isso")

`POST /questions` com algo que o documento enviado não cobre:

```json
{ "question": "Qual é a taxa de administração do fundo ACME Renda Fixa?" }
```

A resposta é uma recusa fundamentada com `sources` vazio (ou o LLM dizendo que
o contexto não cobre). Aqui entra o achado do M8:

> "A recusa é responsabilidade do prompt, não de um threshold de similaridade —
> e isso não é opinião, é medido: no golden dataset, a similaridade dos acertos
> fica em 0,46–0,76 e a dos chunks irrelevantes em 0,55–0,71. As faixas se
> sobrepõem; nenhum corte separa os dois casos sem matar acertos legítimos."

### 4:15–5:00 — Fechamento com os números

> "Como eu sei que a busca funciona? Golden dataset de 25 casos e um harness de
> avaliação (`./gradlew ragEval`): **recall@5 = 95%, MRR = 0,75**. O `topK=5`
> não foi chute — o grid de calibração mostrou que k=3 perde 9 pontos de recall
> e k=8 não ganha nada. Cada rodada custa ~US$ 0,0001. E eu conheço a falha:
> uma pergunta com 'bitcoin' não acha o chunk que fala 'criptoativos' — gap de
> vocabulário, o caso clássico para re-ranking, que é o próximo passo com a
> régua já pronta para medir o ganho."

Se sobrar tempo: `GET /actuator/prometheus` e mostrar
`finrag_pipeline_stage_duration` (latência por etapa: embedding, search, llm).

## Bônus (se a conversa esticar)

Enviar o `faq-fundos.md` (`POST /documents`) e repetir a pergunta da taxa de
administração: agora ela **tem** resposta (0,8% a.a. + performance de 20% sobre
o que exceder 100% do CDI) — o contraste recusa → resposta após ingestão mostra o RAG
funcionando de ponta a ponta em menos de um minuto.

## Plano B — Render fora do ar

O roteiro é idêntico rodando local:

```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
docker compose up
```

Swagger em `http://localhost:8080/swagger-ui.html` e os mesmos passos acima.

## Perguntas prováveis depois da demo

As respostas preparadas, decisão por decisão (pgvector vs vector DB dedicado,
por que sem framework de RAG, por que avaliar só retrieval, etc.), estão em
[`adr-defesa.md`](adr-defesa.md). Metodologia e números completos da avaliação
em [`rag-eval.md`](rag-eval.md).

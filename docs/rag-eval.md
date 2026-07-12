# Avaliação de RAG — metodologia e resultados (M8)

Como saber se a busca semântica do FinRAG retorna os chunks certos? Este
documento registra a metodologia do harness de avaliação de retrieval, os
resultados da calibração de `topK`/`minSimilarity` e as limitações conscientes.
Specs completas em `specs/M8-*.md`; ADR-07 em `specs/00-architecture.md`.

## Metodologia

- **Golden dataset**: 25 casos curados em
  `src/test/resources/golden/golden-dataset.json` sobre um corpus fictício de
  3 documentos financeiros (relatório anual, política de investimentos, FAQ de
  fundo — ~20 chunks). Cada caso aponta o documento-fonte e uma substring que
  deve estar no chunk retornado. Composição: 18 diretos, 1 multi-chunk (exige
  dois chunks), 4 ambíguos entre documentos e 3 **sem resposta no corpus**.
  As perguntas são parafraseadas — nunca frase literal do documento — para não
  inflar a similaridade.
- **O que se mede** (só retrieval — a resposta nunca é melhor que o contexto
  que chega ao prompt):
  - **recall@k**: o(s) chunk(s) esperado(s) apareceram no top-k acima do
    threshold? (estrito: caso multi-chunk exige todos)
  - **MRR**: 1/posição do primeiro chunk relevante (caso que falha conta 0)
  - **refusalAccuracy**: nos casos sem resposta, nada passou do threshold?
- **Mecânica do grid**: cada pergunta é embedada **uma vez** e busca-se o
  top-10 com scores; todas as combinações `topK ∈ {3,5,8}` ×
  `minSimilarity ∈ {0.15,0.25,0.35,0.45}` são pós-processamento em memória —
  o grid inteiro custa uma única rodada de embeddings.
- **Execução**: `OPENAI_API_KEY=... ./gradlew ragEval` (Testcontainers +
  pipeline real de ingestão + API real da OpenAI). Fora do build/CI padrão.
  Custo por rodada: ~5,2k tokens no `text-embedding-3-small` ≈ **US$ 0,0001**.
  Relatório bruto em `build/reports/rag-eval/report.md`.

## Resultados (2026-07-12)

| topK | minSimilarity | recall | MRR | refusalAccuracy |
|------|---------------|--------|-----|-----------------|
| 3 | 0.15–0.45 | 86% | 0,73 | 0% |
| **5 (produção)** | **0.15–0.45** | **95%** | **0,75** | **0%** |
| 8 | 0.15–0.45 | 95% | 0,75 | 0% |

(As quatro colunas de threshold estão colapsadas porque as métricas são
idênticas em todo o range — ver achado 2.)

### Decisão: manter `topK = 5` e `minSimilarity = 0.25`

- **`topK = 5` validado pelos dados**: dois acertos legítimos chegam no rank 5
  (`hedge-cambial-varejo`, `taxa-administracao`) — `k=3` perderia 9 p.p. de
  recall; `k=8` não recupera nenhum caso a mais e só encareceria o prompt.
- **`minSimilarity = 0.25` mantido como piso de sanidade**, ciente de que ele
  não faz recusa (achado 2). Subir o threshold cortaria acertos: o acerto mais
  "frouxo" tem similaridade 0,46.

## Achados

1. **Recall@5 = 95%** (21/22 casos com resposta), com MRR 0,75 — a maioria dos
   acertos vem em 1º lugar.
2. **Nenhum threshold separa "tem resposta" de "não tem"**: a similaridade dos
   acertos fica em **0,46–0,76** e a do 1º chunk irrelevante dos casos sem
   resposta em **0,55–0,71** — sobreposição quase total. Para recusar o caso
   mais difícil (pergunta sobre rentabilidade, que o FAQ menciona sem dar o
   número, 0,71) o corte precisaria de 0,72 — o que mataria 21 dos 22 acertos.
   Com embeddings da OpenAI, perguntas do mesmo domínio rendem ~0,5+ de
   cosseno mesmo sem resposta no corpus. **A recusa é responsabilidade do
   prompt do RAG** (o LLM instruído a responder apenas com base no contexto),
   não do threshold — agora com evidência, não opinião.
3. **A falha conhecida** (`vedacao-cripto`): a pergunta usa "bitcoin", o corpus
   diz "criptoativos"; o chunk certo aparece só no **rank 9 (0,44)**, atrás de
   8 chunks genéricos. Gap de vocabulário é o caso de uso clássico de
   **re-ranking** — registrado como candidato M9, e este harness é a régua
   que mediria o ganho.

## Limitações conscientes

- 25 casos não têm valor estatístico — o entregável é o **método** e a régua
  para mudanças futuras (re-ranking, ajustes de chunking), não um benchmark.
- Quem escreveu o corpus escreveu as perguntas (mitigado com paráfrases, casos
  ambíguos e casos sem resposta, mas o viés existe).
- Só retrieval: a qualidade da resposta gerada não tem métrica automatizada —
  LLM-as-judge ficou no backlog (M9) por custo e não-determinismo.

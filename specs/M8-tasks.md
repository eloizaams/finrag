# M8 — Avaliação de RAG — Tasks

> Antes da primeira edição em código: passar pelo fluxo `explain-then-code` com
> os conceitos do marco — recall@k / hit rate, MRR, por que avaliar retrieval
> separado da geração, e a mecânica do grid de calibração.

## Preparação

- [x] Criar branch `feature/m8-avaliacao-rag` a partir de `develop`
- [x] Atualizar `specs/01-roadmap.md`: M8 alocado (tema, motivação, status 🔄)
- [x] Atualizar tabela de progresso do `README.md` (M8 em andamento)

## Corpus e golden dataset

- [x] Escrever 2–3 documentos financeiros fictícios em Markdown em
      `src/test/resources/golden/corpus/` — ricos o bastante para 15–25
      perguntas, com temas que se sobreponham parcialmente entre documentos
- [x] Definir o schema do caso em `golden-dataset.json` (pergunta, documento
      esperado, substrings esperadas, tem-resposta?, resposta de referência)
- [x] Escrever os 15–25 casos: perguntas **parafraseadas** (nunca frase literal
      do documento), ≥ 1 caso multi-chunk, ≥ 2 casos sem resposta no corpus,
      ≥ 2 casos ambíguos entre documentos
- [x] Revisar as substrings-âncora: curtas (3–6 palavras), no miolo de
      parágrafo, únicas no corpus (validadas por script: presentes e únicas)

## Harness (src/test/kotlin/com/eloiza/finrag/eval/)

- [x] `GoldenCase.kt` + `GoldenDatasetLoader.kt`: carregar e validar o JSON
      (falha clara se malformado ou substring duplicada) — com
      `GoldenDatasetLoaderTest` rodando no build normal como guarda de
      consistência dataset↔corpus no CI
- [x] `RetrievalMetrics.kt`: recall@k e MRR por caso; agregação por combinação
      do grid `topK × minSimilarity`; regra dos casos sem resposta (acerto =
      nenhum score acima do threshold)
- [x] Teste unitário de `RetrievalMetrics` com resultados sintéticos (roda no
      build normal, sem tag — a lógica de métrica é código como outro qualquer)
- [x] `RagRetrievalEvalSpec.kt` com `@Tags("RagEval")`: sobe contexto +
      Testcontainers, cria usuário sintético, ingere o corpus via
      `IngestDocumentUseCase`, embeda perguntas, busca top-10 via
      `ChunkSearchRepository`, computa o grid em memória
- [x] `RagEvalReportWriter.kt`: resumo no console + markdown em
      `build/reports/rag-eval/report.md` (tabela do grid, casos que falharam
      com os chunks retornados no lugar)
- [x] Spec não falha por métrica baixa; falha por erro de execução (chave
      ausente, dataset inválido)

## Build (build.gradle.kts)

- [x] `tasks.test`: excluir a tag (`kotest.tags = !RagEval`)
- [x] Task `ragEval` (tipo `Test`): incluir só a tag, sobrescrever a
      `OPENAI_API_KEY` fake do `withType<Test>` com a real do ambiente, e
      falhar rápido com mensagem clara se não estiver definida
- [x] Confirmar: `./gradlew build` verde **sem** nenhuma chamada externa e sem
      executar a spec de avaliação (spec ausente dos test-results; ragEval sem
      chave falha rápido com a mensagem esperada)

## Calibração

- [x] Rodar `OPENAI_API_KEY=... ./gradlew ragEval` e revisar o relatório
      (duas rodadas em 2026-07-12; a segunda com scores dos acertos no relatório)
- [x] Escolher `topK`/`minSimilarity` finais: **mantidos 5 / 0.25** — k=5
      validado (recall 95% vs 86% em k=3; k=8 não ganha nada); threshold inerte
      em 0.15–0.45 e sem ponto de corte possível (faixas 0,46–0,76 vs 0,55–0,71
      se sobrepõem) — recusa delegada ao prompt. Justificativa em
      `docs/rag-eval.md`
- [x] Se os valores mudarem: atualizar `RagProperties`/`application.yaml` e
      validar que os testes existentes continuam verdes (não mudaram — nada a
      atualizar)
- [x] Estimar e anotar o custo por execução do harness: ~5,2k tokens ≈
      US$ 0,0001/rodada (documentado em `docs/rag-eval.md`)

## Documentação e fechamento do marco

- [x] `docs/rag-eval.md`: metodologia, tabela final do grid, valores escolhidos
      e justificativa, como rodar o harness
- [x] README: seção "Avaliação de RAG" (resumo dos números + link para
      `docs/rag-eval.md`) e atualizar a tabela de progresso (M8 ✅)
- [x] ADR-07 em `specs/00-architecture.md`: estratégia de avaliação (golden
      dataset + métricas de retrieval, sem framework pronto — coerente com
      ADR-04) e limites conscientes (dataset pequeno, sem LLM-as-judge)
- [x] Decidir e registrar: LLM-as-judge vai para o **backlog M9** (custo e
      não-determinismo; registrado no roadmap e no ADR-07)
- [x] Marcar progresso em `00-architecture.md`/`01-roadmap.md`
- [x] Commits semânticos ao longo do marco; `./gradlew build` limpo antes do PR
- [x] PR `feature/m8-avaliacao-rag → develop` (#19, mergeado); release
      `develop → main` (#20, mergeado — deploy no Render disparado; sem
      mudança em `src/main`, rebuild da mesma aplicação)

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M8-requirements.md` atendidos — em especial:
métricas de retrieval medidas sobre o golden dataset, `topK`/`minSimilarity`
escolhidos com justificativa baseada nos números, `./gradlew build` sem chamadas
externas, e metodologia + resultados publicados no README/`docs/rag-eval.md`.

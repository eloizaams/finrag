# M8 — Avaliação de RAG — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| O que medir | Só retrieval (busca semântica), via `ChunkSearchRepository` direto | Retrieval é a variável que `topK`/`minSimilarity` controlam — é o que a calibração precisa isolar. É determinístico e barato (só embeddings de pergunta). Avaliar geração junto misturaria duas fontes de erro na mesma métrica; o argumento clássico de avaliação de RAG é justamente separar "achou o contexto certo?" de "respondeu bem com ele?". A montagem de prompt já tem testes próprios (`RagPromptBuilderTest`) |
| Identificação do acerto | Documento-fonte esperado + substring única que deve estar no `content` do chunk retornado | Ids de chunk mudam a cada re-ingestão (UUID) e quebram se o chunking mudar; só o documento é métrica frouxa demais com um corpus pequeno. A dupla doc+substring é robusta a mudanças de chunking e legível no arquivo do dataset |
| Formato do dataset | JSON em `src/test/resources/golden/golden-dataset.json` | Jackson já está no classpath (Spring Web); JSON é diff-friendly e não adiciona dependência de parser YAML |
| Corpus | 2–3 documentos financeiros **novos** em Markdown, criados para a avaliação | As fixtures de teste atuais são rasas demais — métricas sairiam artificialmente altas. Documentos ricos (ex.: relatório anual fictício, política de investimentos, FAQ de fundos) permitem 15–25 perguntas com respostas distribuídas e perguntas ambíguas entre documentos |
| Onde vive o harness | `src/test`, spec Kotest com tag `RagEval` | Reusa `PostgresTestContainer`, o boot do contexto Spring e toda a infra de teste existente, sem configurar source set novo. Mais simples de explicar e manter |
| Como excluir/rodar | Tag Kotest via system property: `test` roda com `kotest.tags = !RagEval`; task nova `ragEval` (tipo `Test`) roda com `kotest.tags = RagEval` | Mecanismo nativo do Kotest para incluir/excluir specs por tag, sem gambiarras de nome de classe. Garante o critério 5 (build/CI nunca chama API externa) |
| Chave da OpenAI no `ragEval` | A task `ragEval` sobrescreve `OPENAI_API_KEY` com o valor real do ambiente | **Pegadinha**: `tasks.withType<Test>` já injeta uma chave fake para os testes normais — e também se aplicaria à `ragEval`. A task precisa reexportar a variável real (e falhar rápido com mensagem clara se ela não estiver definida) |
| Estratégia do grid | Embedar cada pergunta **uma vez**, buscar top-10 com scores, e computar as métricas de todas as combinações `topK ∈ {3, 5, 8}` × `minSimilarity ∈ {0.15, 0.25, 0.35, 0.45}` em memória | Os scores de similaridade já vêm na busca (`ScoredChunk`); cortar em k menor ou aplicar threshold diferente é pós-processamento. O grid inteiro custa o mesmo que uma execução simples — nenhuma chamada extra à OpenAI |
| Métricas | hit rate / recall@k (chunk esperado aparece no top-k acima do threshold?) e MRR (1/posição do primeiro acerto) por caso, agregadas por combinação do grid. Casos "sem resposta": acerto = nenhum chunk acima do threshold | Recall@k responde "o contexto certo chegou ao prompt?"; MRR diferencia "apareceu em 1º" de "apareceu em 5º" — relevante porque o prompt concatena os chunks em ordem. São as duas métricas padrão de retrieval, fáceis de defender |
| Relatório | Resumo no console + markdown em `build/reports/rag-eval/report.md`; após a calibração, uma cópia curada vai para `docs/rag-eval.md` (versionada) e o README linka/resume | `build/` é a convenção Gradle para saída de execução (descartável); o que se versiona é o resultado analisado, não o artefato bruto de cada rodada |
| Usuário/dados da execução | Usuário sintético criado no próprio banco efêmero do Testcontainers | A busca filtra por `userId` (`findMostSimilar(userId, ...)`) — o harness reproduz o cenário real de escopo por usuário sem tocar em nada persistente |

## Onde entra cada peça (Clean Architecture)

Nenhum código de produção novo — o marco inteiro vive em `src/test` (avaliação é
ferramenta de qualidade, não funcionalidade) e, se a calibração indicar, muda
apenas valores default em `RagProperties`/`application.yaml`.

```
src/test/
├── kotlin/com/eloiza/finrag/eval/
│   ├── GoldenCase.kt              # data class: pergunta, docEsperado, substrings, temResposta
│   ├── GoldenDatasetLoader.kt     # lê golden-dataset.json via Jackson
│   ├── RetrievalMetrics.kt        # recall@k, MRR, agregação por combinação do grid
│   ├── RagEvalReportWriter.kt     # renderiza console + build/reports/rag-eval/report.md
│   └── RagRetrievalEvalSpec.kt    # spec Kotest @Tags("RagEval"): orquestra ingestão → busca → métricas
└── resources/golden/
    ├── golden-dataset.json        # os 15–25 casos
    └── corpus/
        ├── relatorio-anual-acme.md      # fixtures novas (nomes ilustrativos,
        ├── politica-investimentos.md    #  definir na implementação)
        └── faq-fundos.md
```

Peças de produção **reutilizadas** (nenhuma alterada):
`IngestDocumentUseCase` (indexa o corpus pelo pipeline real),
`EmbeddingProvider`/`OpenAiEmbeddingProvider` (embedding das perguntas),
`ChunkSearchRepository`/`ChunkSearchRepositoryJpaAdapter` (busca com score),
`PostgresTestContainer` (pgvector efêmero).

## Fluxo principal do harness

1. `RagRetrievalEvalSpec` sobe o contexto Spring + Postgres com pgvector
   (Testcontainers), como os testes de integração existentes — mas com a
   OpenAI **real** (a task `ragEval` injeta a chave verdadeira).
2. Cria um usuário sintético e ingere os documentos de
   `resources/golden/corpus/` via `IngestDocumentUseCase` (chunking e
   embeddings reais, exatamente como em produção).
3. Carrega `golden-dataset.json`. Para cada caso: embeda a pergunta e chama
   `ChunkSearchRepository.findMostSimilar(userId, embedding, k = 10)`.
4. Marca acerto por posição: o chunk retornado pertence ao documento esperado
   **e** contém a substring esperada.
5. Computa, em memória, recall@k e MRR para cada combinação do grid
   `topK × minSimilarity`; casos "sem resposta" contam acerto quando nenhum
   score supera o threshold da combinação.
6. Imprime o resumo no console e grava `build/reports/rag-eval/report.md`
   com: tabela do grid, métricas por caso e lista dos casos que falharam
   (com os chunks que vieram no lugar — insumo para depurar chunking/dataset).
7. A spec **não falha** por métrica baixa (é medição, não asserção de CI);
   falha apenas por erro de execução (chave ausente, dataset malformado).

## Fluxo de calibração (uma vez, com o relatório em mãos)

1. Rodar `OPENAI_API_KEY=... ./gradlew ragEval`.
2. Ler a tabela do grid: escolher a combinação que maximiza recall nos casos
   com resposta **sem** derrubar o acerto dos casos sem resposta (threshold
   baixo demais = contexto lixo no prompt; alto demais = recusa indevida).
3. Se a escolha diferir de `topK = 5` / `minSimilarity = 0.25`, atualizar
   `RagProperties` e o yaml; registrar a justificativa no ADR-07 e em
   `docs/rag-eval.md`.
4. Copiar o resumo curado para `docs/rag-eval.md` e atualizar o README.

## Migration Flyway

Nenhuma — o harness só usa o schema existente em banco efêmero.

## Dependências novas (build.gradle.kts)

Nenhuma esperada. Possível exceção: `testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")`
se a desserialização do JSON em data classes Kotlin exigir (verificar na
implementação — pode já vir transitivamente).

Mudanças de build (não são dependências):

- `tasks.test`: `systemProperty("kotest.tags", "!RagEval")`
- Task nova `ragEval` (tipo `Test`, mesmo classpath de teste):
  `systemProperty("kotest.tags", "RagEval")` + `OPENAI_API_KEY` real do
  ambiente, com falha imediata e mensagem clara se ausente.

## Configuração (application.yaml)

Nenhuma chave nova. Único efeito possível do marco na configuração: mudança dos
**valores** de `finrag.rag.top-k` / `min-similarity` se a calibração indicar.

## Trade-offs conscientes

- **Dataset pequeno e autoral**: 15–25 casos não têm valor estatístico — o
  entregável é o *método* e a régua para mudanças futuras (re-ranking do M9,
  ajuste de chunking), não um benchmark. Dito explicitamente no ADR-07.
- **Risco de auto-confirmação**: quem escreve o corpus escreve as perguntas.
  Mitigação: perguntas parafraseadas (nunca copiar frases literais do
  documento), casos ambíguos entre documentos e casos sem resposta.
- **Substring como âncora**: pode quebrar se o chunking cortar no meio dela.
  Mitigação: substrings curtas (3–6 palavras), no miolo de um parágrafo.
- **Embeddings reais = não 100% reprodutível**: a API pode mudar o modelo por
  trás do alias. Aceitável — na prática os embeddings são estáveis, e é o
  mesmo provedor de produção (avaliar com outro seria medir outra coisa).
- **Avaliação só de retrieval**: a qualidade da resposta gerada segue sem
  métrica automatizada. LLM-as-judge fica como extensão opcional (decidir no
  fechamento do marco) — custa chamadas de LLM e é menos determinístico.

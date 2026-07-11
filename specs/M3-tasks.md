# M3 — Q&A RAG — Tasks

## Domínio e portas

- [x] Criar `domain/model/Answer.kt`, ~~`domain/model/Source.kt`~~ e
      `domain/model/ScoredChunk.kt` — *(pós-code-review: `Source` era um
      clone de `ScoredChunk` e foi removido; `Answer.sources` carrega
      `List<ScoredChunk>` direto)*
- [x] Criar exceções de domínio: ~~`BlankQuestionException`~~,
      `LlmProviderException` (reaproveitar `EmbeddingProviderException` do
      M2 para falha de embedding da pergunta) — *(pós-code-review:
      `BlankQuestionException` removida; validação da pergunta virou
      `@field:NotBlank` + `@Valid`, o mesmo mecanismo de Bean Validation do
      M1)*
- [x] Criar `domain/port/ChunkSearchRepository.kt` (interface:
      `findMostSimilar(userId, queryEmbedding, k)`)
- [x] Criar `domain/port/LlmClient.kt` (interface:
      `generate(systemPrompt, userPrompt): String`)
- [x] Implementar `domain/service/RagPromptBuilder.kt` (system prompt fixo +
      injeção dos chunks numerados com filename de origem) + teste unitário
      puro: contexto com um chunk, contexto com múltiplos chunks, pergunta
      vazia (se aplicável validar antes de chegar aqui)

## Casos de uso

- [x] Implementar `application/AskQuestionUseCase.kt` (+ teste unitário com
      fakes de `EmbeddingProvider`/`ChunkSearchRepository`/`LlmClient`:
      fluxo feliz com fontes retornadas, busca vazia devolve resposta padrão
      sem chamar `LlmClient`, falha de embeddings não chama
      `ChunkSearchRepository` nem `LlmClient`, falha do LLM propaga
      `LlmProviderException`) — *(pós-code-review: validação de pergunta em
      branco saiu do use case para `@Valid` no controller; ganhou filtro por
      `finrag.rag.min-similarity`, com testes de chunks abaixo do threshold
      e de só-chunks-irrelevantes)*

## Infraestrutura — persistência

- [x] Criar migration `V4__create_chunks_embedding_index.sql` (índice HNSW
      `vector_cosine_ops` em `chunks.embedding`)
- [x] Implementar `infrastructure/persistence/ChunkSearchRepositoryJpaAdapter.kt`
      (query nativa com `<=>`, filtro por `user_id`, `ORDER BY` distância,
      `LIMIT :k`) — *(pós-code-review: filtro por `user_id` via `join` com
      `documents` fazia o predicado de tenant cair fora do alcance do índice
      HNSW, degradando recall em corpus multi-usuário grande; migration `V5`
      desnormaliza `user_id` em `chunks` e o filtro passou a usar a coluna
      direto)*
- [x] Teste de integração (Testcontainers) do adapter: inserir chunks com
      embeddings determinísticos conhecidos (ex.: vetores próximos vs.
      distantes construídos à mão) e validar que `findMostSimilar` retorna
      na ordem certa; validar que chunks de outro `userId` nunca aparecem no
      resultado; validar que `k` limita corretamente a quantidade retornada

## Infraestrutura — Anthropic

- [x] Criar `infrastructure/anthropic/AnthropicProperties.kt`
      (`@ConfigurationProperties`: `api-key`, `base-url`, `model`,
      `max-tokens`)
- [x] Configurar `finrag.anthropic.*` e `finrag.rag.top-k` no
      `application.yaml` (chave via `ANTHROPIC_API_KEY`, sem default; env
      var de teste adicionada em `build.gradle.kts`, seguindo o padrão já
      usado para `OPENAI_API_KEY` no M2) — *(pós-code-review: `model`,
      `max-tokens`, `top-k` e o novo `min-similarity` tinham default no
      código E no yaml, que sempre prevalecia — duas fontes de verdade.
      Criado `infrastructure/RagProperties.kt` e o yaml reduzido a só
      `api-key`/`base-url`, que de fato precisam vir de env var)*
- [x] Implementar `infrastructure/anthropic/AnthropicLlmClient.kt`
      (`RestClient`, `POST /v1/messages`, headers `x-api-key` +
      `anthropic-version`, mapear falhas para `LlmProviderException` sem
      logar a chave nem headers) — *(pós-code-review: `stop_reason` da
      resposta era ignorado, então um corte por `max_tokens` voltava como se
      a resposta estivesse completa; agora loga um warning quando isso
      acontece)*
- [x] Teste do adapter com `MockRestServiceServer`: request correto (model,
      max_tokens, headers, corpo da mensagem), parse do texto de resposta
      (`content[0].text`), erro HTTP → `LlmProviderException`

## API

- [x] Criar DTOs `QuestionRequest` (question) e `AnswerResponse` (answer,
      sources: List<SourceResponse>)
- [x] Implementar `api/QuestionController.kt` (`POST /questions`, `userId`
      lido do `SecurityContext` via `@AuthenticationPrincipal`) —
      *(pós-code-review: `@Valid` no `@RequestBody` para acionar o
      `@field:NotBlank` de `QuestionRequest`)*
- [x] Implementar ~~`api/QuestionExceptionHandler.kt`~~
      (`@RestControllerAdvice` → `ProblemDetail`: `400` para
      `BlankQuestionException`, `502` para `LlmProviderException` e
      `EmbeddingProviderException`; atenção ao `@Order(HIGHEST_PRECEDENCE)`
      — lição aprendida no M2) — *(pós-code-review: o handler de
      `EmbeddingProviderException` estava duplicado, palavra por palavra,
      em `DocumentExceptionHandler`. Extraído para
      `api/ProviderExceptionHandler.kt`, um `@RestControllerAdvice` global
      para `EmbeddingProviderException`/`LlmProviderException`; o 400 de
      pergunta em branco passou a vir do `MethodArgumentNotValidException`
      já tratado em `SecurityExceptionHandler`)*
- [x] Registrar `AskQuestionUseCase` e os novos adapters no `UseCaseConfig`
      (composition root); `RagPromptBuilder` também vira bean se precisar de
      configuração
- [x] Adicionar `ANTHROPIC_API_KEY` ao serviço `app` no `docker-compose.yml`

## Testes de integração (Kotest + Testcontainers)

> Fake de `EmbeddingProvider` via `@TestConfiguration` — **não** o fake do M2
> (`ControllableFakeEmbeddingProvider`, que gera um vetor uniforme e por isso
> qualquer par de chunks fica com similaridade sempre 1.0 ou -1.0, inútil
> pra testar ranking). Criado `BagOfWordsFakeEmbeddingProvider`: cada palavra
> incrementa uma dimensão fixa (hash da palavra) — textos que compartilham
> vocabulário ficam mais próximos por cosseno, preservando a propriedade que
> o teste de ranking precisa, sem chamar a OpenAI de verdade. Fake de
> `LlmClient` (`ControllableFakeLlmClient`, resposta determinística + modo
> de falha) via `@TestConfiguration` — nem OpenAI nem Anthropic são chamadas
> de verdade nos testes.

- [x] `POST /questions` com contexto encontrado → `200`, resposta e fontes
      no corpo, chunk mais similar aparece primeiro nas fontes
- [x] Isolamento por usuário: dois usuários com documentos distintos, cada
      um só recebe fontes dos próprios documentos
- [x] Usuário sem documentos indexados → `200` com resposta padrão e
      `sources` vazio, sem chamar o fake de `LlmClient`
- [x] Pergunta em branco/vazia → `400`
- [x] Fake de embeddings em modo de falha → `502`
- [x] Fake de LLM em modo de falha → `502`
- [x] `POST /questions` sem token → `401`
- [x] *(pós-code-review)* Usuário só com documentos irrelevantes (abaixo do
      threshold de similaridade) → `200` com resposta padrão e `sources`
      vazio, sem chamar o fake de `LlmClient`

## Fechamento do marco

- [x] Rodar `./gradlew build` limpo (build + todos os testes)
- [x] Atualizar README (endpoint `/questions` com exemplo de `curl`, env var
      `ANTHROPIC_API_KEY` obrigatória)
- [x] Validar fluxo completo via `docker compose up` com OpenAI e Anthropic
      reais: perguntar sobre um documento já ingerido (do teste manual do
      M2), conferir resposta e fontes, conferir isolamento entre usuários,
      conferir os erros 400/502 — pegou um problema real não coberto pelos
      testes automatizados: conta da Anthropic sem crédito de API (produto
      separado da assinatura do claude.ai) retorna `400` da Anthropic,
      corretamente mapeado para `502` pelo `AnthropicLlmClient` sem vazar a
      chave no log; após adicionar crédito, fluxo completo (pergunta →
      embedding real → busca vetorial real → resposta do Claude Haiku
      citando a fonte) e isolamento entre dois usuários reais confirmados
- [x] Commit(s) semânticos ao longo da implementação — incluindo os 9
      commits do code review pós-implementação (validação, threshold de
      similaridade, truncamento por `max_tokens`, remoção do `Source`,
      desnormalização de `user_id`, mitigação de prompt injection, fonte
      única de config, docs, Postman)
- [x] Abrir PR de `feature/m3-qa-rag` para `develop` — [PR #6](https://github.com/eloizaams/finrag/pull/6)

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M3-requirements.md` atendidos, testes de
integração cobrindo os fluxos listados acima, CI verde no PR.

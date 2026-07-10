# M3 — Q&A RAG — Tasks

## Domínio e portas

- [ ] Criar `domain/model/Answer.kt`, `domain/model/Source.kt` e
      `domain/model/ScoredChunk.kt`
- [ ] Criar exceções de domínio: `BlankQuestionException`,
      `LlmProviderException` (reaproveitar `EmbeddingProviderException` do
      M2 para falha de embedding da pergunta)
- [ ] Criar `domain/port/ChunkSearchRepository.kt` (interface:
      `findMostSimilar(userId, queryEmbedding, k)`)
- [ ] Criar `domain/port/LlmClient.kt` (interface:
      `generate(systemPrompt, userPrompt): String`)
- [ ] Implementar `domain/service/RagPromptBuilder.kt` (system prompt fixo +
      injeção dos chunks numerados com filename de origem) + teste unitário
      puro: contexto com um chunk, contexto com múltiplos chunks, pergunta
      vazia (se aplicável validar antes de chegar aqui)

## Casos de uso

- [ ] Implementar `application/AskQuestionUseCase.kt` (+ teste unitário com
      fakes de `EmbeddingProvider`/`ChunkSearchRepository`/`LlmClient`:
      fluxo feliz com fontes retornadas, pergunta em branco lança
      `BlankQuestionException`, busca vazia devolve resposta padrão sem
      chamar `LlmClient`, falha de embeddings não chama `ChunkSearchRepository`
      nem `LlmClient`, falha do LLM propaga `LlmProviderException`)

## Infraestrutura — persistência

- [ ] Criar migration `V4__create_chunks_embedding_index.sql` (índice HNSW
      `vector_cosine_ops` em `chunks.embedding`)
- [ ] Implementar `infrastructure/persistence/ChunkSearchRepositoryJpaAdapter.kt`
      (query nativa com `<=>`, filtro por `user_id` via join com
      `documents`, `ORDER BY` distância, `LIMIT :k`)
- [ ] Teste de integração (Testcontainers) do adapter: inserir chunks com
      embeddings determinísticos conhecidos (ex.: vetores próximos vs.
      distantes construídos à mão) e validar que `findMostSimilar` retorna
      na ordem certa; validar que chunks de outro `userId` nunca aparecem no
      resultado; validar que `k` limita corretamente a quantidade retornada

## Infraestrutura — Anthropic

- [ ] Criar `infrastructure/anthropic/AnthropicProperties.kt`
      (`@ConfigurationProperties`: `api-key`, `base-url`, `model`,
      `max-tokens`)
- [ ] Configurar `finrag.anthropic.*` e `finrag.rag.top-k` no
      `application.yaml` (chave via `ANTHROPIC_API_KEY`, sem default; env
      var de teste adicionada em `build.gradle.kts`, seguindo o padrão já
      usado para `OPENAI_API_KEY` no M2)
- [ ] Implementar `infrastructure/anthropic/AnthropicLlmClient.kt`
      (`RestClient`, `POST /v1/messages`, headers `x-api-key` +
      `anthropic-version`, mapear falhas para `LlmProviderException` sem
      logar a chave nem headers)
- [ ] Teste do adapter com `MockRestServiceServer`: request correto (model,
      max_tokens, headers, corpo da mensagem), parse do texto de resposta
      (`content[0].text`), erro HTTP → `LlmProviderException`

## API

- [ ] Criar DTOs `QuestionRequest` (question) e `AnswerResponse` (answer,
      sources: List<SourceResponse>)
- [ ] Implementar `api/QuestionController.kt` (`POST /questions`, `userId`
      lido do `SecurityContext` via `@AuthenticationPrincipal`)
- [ ] Implementar `api/QuestionExceptionHandler.kt`
      (`@RestControllerAdvice` → `ProblemDetail`: `400` para
      `BlankQuestionException`, `502` para `LlmProviderException` e
      `EmbeddingProviderException`; atenção ao `@Order(HIGHEST_PRECEDENCE)`
      — lição aprendida no M2)
- [ ] Registrar `AskQuestionUseCase` e os novos adapters no `UseCaseConfig`
      (composition root); `RagPromptBuilder` também vira bean se precisar de
      configuração
- [ ] Adicionar `ANTHROPIC_API_KEY` ao serviço `app` no `docker-compose.yml`

## Testes de integração (Kotest + Testcontainers)

> Fake de `EmbeddingProvider` (reaproveitado do M2) + fake de `LlmClient`
> via `@TestConfiguration` (resposta determinística; modo de falha para o
> cenário de erro) — nem OpenAI nem Anthropic são chamadas de verdade nos
> testes.

- [ ] `POST /questions` com contexto encontrado → `200`, resposta e fontes
      no corpo, chunk mais similar aparece primeiro nas fontes
- [ ] Isolamento por usuário: dois usuários com documentos distintos, cada
      um só recebe fontes dos próprios documentos
- [ ] Usuário sem documentos indexados → `200` com resposta padrão e
      `sources` vazio, sem chamar o fake de `LlmClient`
- [ ] Pergunta em branco/vazia → `400`
- [ ] Fake de embeddings em modo de falha → `502`
- [ ] Fake de LLM em modo de falha → `502`
- [ ] `POST /questions` sem token → `401`

## Fechamento do marco

- [ ] Rodar `./gradlew build` limpo (build + todos os testes)
- [ ] Atualizar README (endpoint `/questions` com exemplo de `curl`, env var
      `ANTHROPIC_API_KEY` obrigatória)
- [ ] Validar fluxo completo via `docker compose up` com OpenAI e Anthropic
      reais: perguntar sobre um documento já ingerido (do teste manual do
      M2), conferir resposta e fontes, conferir isolamento entre usuários,
      conferir os erros 400/502
- [ ] Commit(s) semânticos ao longo da implementação
- [ ] Abrir PR de `feature/m3-qa-rag` para `develop`, CI verde

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M3-requirements.md` atendidos, testes de
integração cobrindo os fluxos listados acima, CI verde no PR.

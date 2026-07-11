# M4 — Observabilidade do pipeline RAG — Tasks

## Domínio e portas

- [x] Criar `domain/model/LlmResponse.kt` (`text`, `promptTokens`, `completionTokens`)
- [x] Criar `domain/port/PipelineMetrics.kt` (interface: `recordDuration(pipeline, stage,
      block)`, `recordTokens(promptTokens, completionTokens)`)
- [x] Atualizar `domain/port/LlmClient.kt`: `generate` passa a retornar `LlmResponse`
      em vez de `String`

## Casos de uso

- [x] Atualizar `application/AskQuestionUseCase.kt`: envolver
      `embeddingProvider.embed`/`chunkSearchRepository.findMostSimilar`/`llmClient.generate`
      com `pipelineMetrics.recordDuration("question", <stage>)`; registrar tokens via
      `pipelineMetrics.recordTokens(...)` após a chamada ao LLM; montar `Answer` a partir
      de `LlmResponse.text`
- [x] Atualizar teste unitário de `AskQuestionUseCase` com fake de `PipelineMetrics` e
      fake de `LlmClient` retornando `LlmResponse`
- [x] Atualizar `application/IngestDocumentUseCase.kt`: envolver
      `textExtractor.extract`/`textChunker.chunk`/`embeddingProvider.embed` com
      `pipelineMetrics.recordDuration("ingestion", <stage>)`
- [x] Atualizar teste unitário de `IngestDocumentUseCase` com fake de `PipelineMetrics`

## Infraestrutura — observabilidade

- [x] Adicionar dependência `io.micrometer:micrometer-registry-prometheus`
- [x] Implementar `infrastructure/observability/MicrometerPipelineMetrics.kt`
      (implementa `PipelineMetrics` com `Timer`/`Counter` do `MeterRegistry`;
      métrica `finrag.pipeline.stage.duration` com tags `pipeline`/`stage`; métrica
      `finrag.llm.tokens` com tag `type=prompt|completion`)
- [x] Teste unitário de `MicrometerPipelineMetrics` com `SimpleMeterRegistry`: duração
      registrada com as tags corretas, contagem de tokens incrementada corretamente
- [x] Registrar `MicrometerPipelineMetrics` como bean em `infrastructure/UseCaseConfig.kt`
      (ou configuração dedicada) e injetar nos use cases — via `@Component` (component
      scan já cobre `infrastructure/`), sem precisar de `@Bean` explícito

## Infraestrutura — Anthropic

- [x] Atualizar `infrastructure/anthropic/AnthropicLlmClient.kt`: parsear `usage.input_tokens`/
      `usage.output_tokens` da resposta de `/v1/messages`, retornar `LlmResponse`
- [x] Atualizar `AnthropicLlmClientTest` (via `MockRestServiceServer`) para cobrir o
      parsing de `usage` e o novo tipo de retorno

## API

- [x] Atualizar `api/ProviderExceptionHandler.kt`: incrementar `finrag.provider.errors`
      (tags `provider=openai|anthropic`, `error_type=<classe da causa da falha>`) ao tratar
      `EmbeddingProviderException`/`LlmProviderException`
- [x] Teste cobrindo o incremento do contador de erro ao simular falha de cada provedor
      (em `QuestionControllerTest`, que já simulava as duas falhas) — também aproveitado
      para cobrir `finrag.llm.tokens` incrementando numa pergunta respondida com sucesso

## Configuração

- [x] Configurar `management.endpoints.web.exposure.include: health, prometheus` no
      `application.yaml`
- [x] Configurar `logging.structured.format.console: ecs`
- [x] Liberar `/actuator/prometheus` em `SecurityConfig` (mesmo padrão de
      `/actuator/health`) — necessário além do `application.yaml`, senão o Spring
      Security barra a rota com `401`

> Tentativa de correlation ID (`spring-boot-micrometer-tracing-brave` +
> `management.tracing.sampling.probability: 1.0`) foi revertida — o `trace.id`
> nunca apareceu no log real mesmo com o bean `Tracer` presente. Detalhe
> completo da investigação e da decisão de reverter em `M4-design.md`
> ("Nota: correlation ID não entregue neste marco").

## Testes de integração

- [x] `GET /actuator/prometheus` responde `200` com métricas no formato Prometheus,
      sem autenticação (`SecurityConfigTest`)
- [x] Após `POST /questions` respondida com sucesso, métrica `finrag.llm.tokens`
      incrementou (prompt e completion)
- [x] Após falha simulada do provedor OpenAI (embeddings) ou Anthropic (LLM), métrica
      `finrag.provider.errors` incrementou com a tag de provedor correta
- [x] Nenhum dado sensível (senha, token JWT, corpo da pergunta) aparece nos eventos de
      log capturados no teste de falha de provedor (`QuestionControllerTest`)

## Fechamento do marco

- [x] Rodar `./gradlew build` limpo (build + todos os testes)
- [x] Atualizar README (seção de observabilidade: `GET /actuator/prometheus`, formato de
      log estruturado, como ajustar nível de log via `LOGGING_LEVEL_ROOT`)
- [x] Atualizar `specs/00-architecture.md`/`specs/01-roadmap.md`: marcar M4 como
      concluído
- [x] Validar fluxo completo via `docker compose up` de verdade: uma pergunta real,
      inspecionar log estruturado emitido e `/actuator/prometheus` com as métricas
      esperadas — confirmado `finrag_pipeline_stage_duration_seconds` e
      `finrag_provider_errors_total` no formato correto, logs em JSON ECS
- [x] Commit(s) semânticos ao longo da implementação
- [x] Abrir PR de `feature/m4-observabilidade` para `develop` (PR #8)

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M4-requirements.md` atendidos, testes de integração
cobrindo os fluxos listados acima, CI verde no PR.

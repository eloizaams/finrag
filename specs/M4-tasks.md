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

- [ ] Adicionar dependências `io.micrometer:micrometer-registry-prometheus` e
      `io.micrometer:micrometer-tracing-bridge-brave`
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

- [ ] Atualizar `api/ProviderExceptionHandler.kt`: incrementar `finrag.provider.errors`
      (tags `provider=openai|anthropic`, `error_type=<nome da exceção>`) ao tratar
      `EmbeddingProviderException`/`LlmProviderException`
- [ ] Teste cobrindo o incremento do contador de erro ao simular falha de cada provedor

## Configuração

- [ ] Configurar `management.endpoints.web.exposure.include: health, prometheus` no
      `application.yaml`
- [ ] Configurar `management.tracing.sampling.probability: 1.0`
- [ ] Configurar `logging.structured.format.console: ecs`

## Testes de integração

- [ ] `GET /actuator/prometheus` responde `200` com métricas no formato Prometheus,
      sem autenticação
- [ ] Log de uma requisição a `POST /questions` contém `trace.id`/`transaction.id`
      preenchidos (capturado via `OutputCaptureExtension`/`CapturedOutput` do Spring
      Boot Test, parseando a saída como JSON)
- [ ] Após `POST /questions` respondida com sucesso, métrica `finrag.llm.tokens`
      incrementou (prompt e completion)
- [ ] Após falha simulada do provedor OpenAI (embeddings) ou Anthropic (LLM), métrica
      `finrag.provider.errors` incrementou com a tag de provedor correta
- [ ] Nenhum dado sensível (senha, token JWT, chave de API, corpo da pergunta/resposta)
      aparece na saída de log capturada nos testes acima

## Fechamento do marco

- [ ] Rodar `./gradlew build` limpo (build + todos os testes)
- [ ] Atualizar README (seção de observabilidade: `GET /actuator/prometheus`, formato de
      log estruturado, como ajustar nível de log via `LOGGING_LEVEL_ROOT`)
- [ ] Atualizar `specs/00-architecture.md`/`specs/01-roadmap.md`: marcar M4 como
      concluído
- [ ] Validar fluxo completo via `docker compose up` de verdade: uma pergunta real,
      inspecionar log estruturado emitido e `/actuator/prometheus` com as métricas
      esperadas
- [ ] Commit(s) semânticos ao longo da implementação
- [ ] Abrir PR de `feature/m4-observabilidade` para `develop`

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M4-requirements.md` atendidos, testes de integração
cobrindo os fluxos listados acima, CI verde no PR.

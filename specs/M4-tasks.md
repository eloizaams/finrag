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

- [x] Atualizar `api/ProviderExceptionHandler.kt`: incrementar `finrag.provider.errors`
      (tags `provider=openai|anthropic`, `error_type=<nome da exceção>`) ao tratar
      `EmbeddingProviderException`/`LlmProviderException`
- [x] Teste cobrindo o incremento do contador de erro ao simular falha de cada provedor
      (em `QuestionControllerTest`, que já simulava as duas falhas) — também aproveitado
      para cobrir `finrag.llm.tokens` incrementando numa pergunta respondida com sucesso

## Configuração

- [x] Configurar `management.endpoints.web.exposure.include: health, prometheus` no
      `application.yaml`
- [x] Configurar `management.tracing.sampling.probability: 1.0`
- [x] Configurar `logging.structured.format.console: ecs`
- [x] Liberar `/actuator/prometheus` em `SecurityConfig` (mesmo padrão de
      `/actuator/health`) — necessário além do `application.yaml`, senão o Spring
      Security barra a rota com `401`
- [x] Dependência correta descoberta na prática: `io.micrometer:micrometer-tracing-bridge-brave`
      sozinho **não** cria o bean `Tracer` neste Spring Boot 4.1 — a autoconfiguração
      mudou de módulo. A dependência certa é
      `org.springframework.boot:spring-boot-micrometer-tracing-brave` (mesmo padrão do
      `spring-boot-starter-micrometer-metrics` já usado para métricas); ajustado no
      `build.gradle.kts`

## Testes de integração

- [x] `GET /actuator/prometheus` responde `200` com métricas no formato Prometheus,
      sem autenticação (`SecurityConfigTest`)
- [ ] Log de uma requisição a `POST /questions` contém `trace.id`/`transaction.id`
      preenchidos — **não automatizado**: `Tracer` bean confirmado no contexto e
      `management.tracing.sampling.probability: 1.0` configurado, mas o teste com
      `ListAppender` + `tracer.withSpan(...)` deu resultado inconsistente entre
      execuções (MDC ora populado, ora vazio) sem causa raiz clara dentro do tempo
      disponível — provável timing/ordem de inicialização do
      `MDCScopeDecorator` do Brave nesta combinação de versões (Micrometer Tracing
      1.7.0 / Spring Boot 4.1.0). Verificar manualmente via `docker compose up`
      (ver "Fechamento do marco") em vez de um teste automatizado frágil
- [x] Após `POST /questions` respondida com sucesso, métrica `finrag.llm.tokens`
      incrementou (prompt e completion)
- [x] Após falha simulada do provedor OpenAI (embeddings) ou Anthropic (LLM), métrica
      `finrag.provider.errors` incrementou com a tag de provedor correta
- [x] Nenhum dado sensível (senha, token JWT, corpo da pergunta) aparece nos eventos de
      log capturados no teste de falha de provedor (`QuestionControllerTest`)

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

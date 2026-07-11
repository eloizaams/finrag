# M4 — Observabilidade do pipeline RAG — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Logging estruturado | *Structured logging* nativo do Spring Boot (`logging.structured.format.console: ecs`) | Recurso do próprio framework, sem dependência nova |
| Correlation ID (`trace.id` no log) | **Tentado e revertido** — ver nota abaixo | Micrometer Tracing (`spring-boot-micrometer-tracing-brave`) foi adicionado e o bean `Tracer` passou a existir, mas o `trace.id` não aparecia no JSON de log em nenhum teste real (via `docker compose up`), mesmo após excluir `NoopTracerAutoConfiguration` (suspeita de ordem de autoconfiguração entre módulos nesta versão do Spring Boot). Sem causa raiz identificada dentro do tempo disponível — revertido para não bloquear o marco (ver "Nota: correlation ID não entregue" abaixo) |
| Instrumentação de duração por etapa | Timer manual explícito, via porta de domínio (`PipelineMetrics`), chamado dentro dos próprios use cases ao redor de cada chamada de porta | Mantém a regra de dependência do projeto: `application` não pode importar `Micrometer`/`MeterRegistry` diretamente. Uma porta (mesmo padrão de `PasswordHasher`/`TokenProvider` no M1) preserva a explicitação desejada — cada `recordDuration(stage) { ... }` no use case mostra exatamente o que está sendo medido — sem acoplar a camada de aplicação a uma biblioteca de infraestrutura |
| Contagem de tokens do LLM | `LlmClient.generate` passa a retornar `LlmResponse(text, promptTokens, completionTokens)` em vez de `String` | A API da Anthropic já devolve `usage.input_tokens`/`usage.output_tokens` na resposta de `/v1/messages` — hoje descartados. É a única forma de atender ao critério 4 (contagem de tokens) sem inventar estimativa própria. Muda a assinatura do port, mas é consequência direta do critério já aprovado no requirements — o texto da resposta (`Answer.text`) não muda para quem consome a API |
| Contagem de erro por provedor | Registrada em `ProviderExceptionHandler` (camada `api`), direto via `MeterRegistry` | Já é o ponto central onde `EmbeddingProviderException`/`LlmProviderException` são capturadas hoje. `api` já depende de infraestrutura no fluxo normal do Spring (injeção de bean), então não precisa de porta de domínio aqui — diferente do timer de duração, que nasce dentro do use case |
| Exposição de métricas | `GET /actuator/prometheus` público, sem autenticação, mesmo padrão de `/actuator/health` desde o M1 | Métricas agregadas (contagens, latências) não vazam dado de negócio nem segredo. Reavaliar se M7 (deploy) expuser a API publicamente sem estar atrás de rede interna — candidato a mover para trás de autenticação no M6 (hardening) |
| Nomenclatura das métricas | `finrag.pipeline.stage.duration` (Timer, tags `pipeline=question\|ingestion`, `stage=...`); `finrag.llm.tokens` (Counter, tag `type=prompt\|completion`); `finrag.provider.errors` (Counter, tags `provider=openai\|anthropic`, `error_type=<nome da exceção>`) | Um nome de métrica por família com tags, em vez de um nome por combinação — é a convenção recomendada do Prometheus/Micrometer (menos séries temporais órfãs, agregação por tag no lugar de por nome) |
| Nível de log por ambiente | Mantém `INFO` como padrão (`logging.level.root` não sobrescrito); documentado que dá para baixar para `DEBUG` via env var `LOGGING_LEVEL_ROOT=DEBUG` sem mudar código | Ajuste fino de log por ambiente (perfis, filtros por pacote) é preocupação de operação, não de instrumentação — cabe melhor no M6 (hardening). Aqui só garante que o mecanismo já funciona de graça via propriedade padrão do Spring Boot |

## Onde entra cada peça (Clean Architecture)

```
domain/
├── model/
│   └── LlmResponse.kt              # text, promptTokens, completionTokens — novo retorno de LlmClient.generate
└── port/
    └── PipelineMetrics.kt          # interface: recordDuration(pipeline, stage, block), recordTokens(prompt, completion)

application/
├── AskQuestionUseCase.kt           # (modificado) envolve embed/find/generate com pipelineMetrics.recordDuration;
│                                   # registra tokens após a chamada ao LLM
└── IngestDocumentUseCase.kt        # (modificado) envolve extract/chunk/embed com pipelineMetrics.recordDuration

infrastructure/
├── observability/
│   └── MicrometerPipelineMetrics.kt # implementa domain/port/PipelineMetrics com Timer/Counter do MeterRegistry
└── anthropic/
    └── AnthropicLlmClient.kt        # (modificado) parseia usage.input_tokens/output_tokens da resposta,
                                      # retorna LlmResponse em vez de String

api/
└── ProviderExceptionHandler.kt      # (modificado) incrementa finrag.provider.errors ao tratar
                                      # EmbeddingProviderException/LlmProviderException
```

**Por que `PipelineMetrics` é porta de domínio e não uma injeção direta de `MeterRegistry`**:
mesma razão de `PasswordHasher`/`TokenProvider` no M1 — os use cases não podem depender de
uma biblioteca de infraestrutura diretamente sem violar a regra de dependência do projeto,
e a porta permite testar `AskQuestionUseCase`/`IngestDocumentUseCase` com um fake, sem subir
Micrometer nem Spring.

## Fluxo instrumentado — pergunta (`POST /questions`)

1. `AskQuestionUseCase.ask` chama `pipelineMetrics.recordDuration("question", "embedding") { embeddingProvider.embed(...) }`
2. Em seguida, `pipelineMetrics.recordDuration("question", "search") { chunkSearchRepository.findMostSimilar(...) }`
3. Se houver chunks relevantes, `pipelineMetrics.recordDuration("question", "llm") { llmClient.generate(...) }`,
   que agora retorna `LlmResponse`
4. `pipelineMetrics.recordTokens(response.promptTokens, response.completionTokens)`
5. `Answer` é montado com `response.text` — contrato de `AskQuestionUseCase.ask` para o
   controller não muda

## Fluxo instrumentado — ingestão (`POST /documents`)

1. `IngestDocumentUseCase.ingest` chama `pipelineMetrics.recordDuration("ingestion", "extraction") { textExtractor.extract(...) }`
2. `pipelineMetrics.recordDuration("ingestion", "chunking") { textChunker.chunk(...) }`
3. `pipelineMetrics.recordDuration("ingestion", "embedding") { embeddingProvider.embed(...) }`

## Nota: correlation ID não entregue neste marco

Tentativa registrada para não se perder o aprendizado: adicionamos
`org.springframework.boot:spring-boot-micrometer-tracing-brave` e
`management.tracing.sampling.probability: 1.0`. O bean `Tracer` passou a existir
no contexto (antes disso, nem isso acontecia — `micrometer-tracing-bridge-brave`
sozinho, sem o módulo `spring-boot-micrometer-tracing-brave`, não é suficiente
no Spring Boot 4.1). Ainda assim, o `trace.id` nunca apareceu no JSON de log em
requisições reais (`docker compose up`, não só testes). Investigação levou à
descoberta de que o mesmo jar registra um `NoopTracerAutoConfiguration` como
fallback (`@ConditionalOnMissingBean(Tracer.class)`) — a hipótese é que ele
"ganha" do Brave por ordem de carregamento entre os dois módulos separados
(`spring-boot-micrometer-tracing` e `spring-boot-micrometer-tracing-brave`).
Excluir `NoopTracerAutoConfiguration` explicitamente via
`@SpringBootApplication(exclude = [...])` não resolveu. Sem mais tempo para
isolar a causa raiz (poderia ser um `CurrentTraceContext`/`MDCScopeDecorator`
que precisa de wiring manual, ou um bug real desta combinação de versões),
a dependência foi **revertida** — fica como candidato a investigação pontual
futura, não como findings falso-positivo escondido.

## Dependências novas (build.gradle.kts)

- `io.micrometer:micrometer-registry-prometheus` (formato de exposição do `/actuator/prometheus`)

## Configuração (application.yaml)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, prometheus

logging:
  structured:
    format:
      console: ecs
```

## Trade-offs conscientes

- **Sem correlation ID (`trace.id`) nos logs**: tentado e revertido nesta versão
  específica do Spring Boot/Micrometer Tracing (ver nota acima) — sem isso, não dá
  para agrupar todos os logs de uma mesma requisição automaticamente; mitigação
  parcial hoje é que cada log de erro já inclui contexto suficiente (mensagem,
  stacktrace, exceção) para diagnóstico pontual, mas não para correlação entre
  camadas numa requisição específica.
- **`/actuator/prometheus` público**: aceitável enquanto a API não está exposta
  publicamente (M7 ainda não aconteceu); reavaliar então.
- **Mudança de assinatura de `LlmClient.generate`**: quebra o contrato existente desde o
  M3 (retornava `String`, agora retorna `LlmResponse`) — é uma migração pontual e pequena
  (um único adapter e um único chamador), aceitável para não inventar uma forma paralela
  de expor contagem de tokens fora do port.
- **Sem estimativa de custo em R$/US$**: fica só como contagem de tokens brutos — traduzir
  para custo monetário exigiria manter tabela de preço por modelo atualizada, fora do
  escopo de instrumentação.

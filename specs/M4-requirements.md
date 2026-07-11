
# M4 — Observabilidade do pipeline RAG — Requirements

## Objetivo do marco

Instrumentar a API com logs estruturados e métricas que permitam responder,
com dados reais, as perguntas que hoje só têm resposta por suposição: quanto
custa uma pergunta (tokens consumidos), onde está a latência do pipeline RAG
(embedding da pergunta → busca vetorial → chamada ao LLM), e com que
frequência cada provedor externo (OpenAI, Anthropic) falha. Sem isso, M5/M6/M7
(gestão de documentos, hardening, deploy) seriam decididos no escuro — e é
tema recorrente em entrevista sobre sistemas com LLM em produção.

## Critérios de aceite

1. Logs são emitidos em formato estruturado (JSON), não texto livre — parseável
   por ferramenta de log (ex.: `jq`, ingestão em ELK/Datadog) sem regex.
2. O pipeline de pergunta (`POST /questions`) e o de ingestão (`POST /documents`)
   registram a duração de cada etapa (embedding da pergunta, busca vetorial,
   chamada ao LLM; extração, chunking, embedding) como métrica.
3. Métricas customizadas via Micrometer expõem, no mínimo: latência (timer)
   de cada etapa do pipeline RAG e do pipeline de ingestão; contagem de tokens
   de prompt/completion consumidos por chamada ao Claude; contagem de erros
   por provedor externo (OpenAI, Anthropic), com tag de tipo de erro.
4. `GET /actuator/prometheus` expõe as métricas em formato Prometheus.
5. Dados sensíveis (senha, token JWT, chave de API, corpo completo de
   pergunta/resposta do usuário) nunca aparecem em log, nem truncados — apenas
   metadados (tamanho, IDs, contagens).
6. Erros não tratados continuam retornando o `ProblemDetail` já existente
   (M1/M3) e adicionalmente são logados com stacktrace.
7. Testes de integração cobrem: métrica de tokens incrementando após uma
   pergunta respondida com sucesso, métrica de erro incrementando quando o
   provedor externo falha, e ausência de dados sensíveis nos logs capturados.

## Fora de escopo neste marco

- Correlation ID / trace ID propagado via MDC (Micrometer Tracing) — tentado
  durante a implementação; o bean `Tracer` não propagava corretamente o
  contexto para o MDC nesta combinação específica de versões (Spring Boot
  4.1.0 / Micrometer Tracing 1.7.0), sem causa raiz identificada dentro do
  tempo disponível. Revertido para não bloquear o marco; candidato a
  investigação pontual futura (ver `M4-design.md`)
- Tracing distribuído (OpenTelemetry/Jaeger/Zipkin) — sistema é um serviço
  único, sem múltiplos serviços para correlacionar
- Dashboards visuais (Grafana) — métricas ficam expostas via `/actuator`,
  visualização fica a critério de quem consumir
- Alerting automático (PagerDuty, Slack, etc.)
- APM comercial (Datadog, New Relic)
- Golden dataset / avaliação de qualidade de resposta do RAG — já registrado
  como candidato a M9 em `CLAUDE.md`/`00-architecture.md`
- Estimativa de custo em R$/US$ por pergunta (fica só como contagem de tokens
  — conversão para custo monetário depende de tabela de preço que muda com
  frequência e não é responsabilidade da aplicação)

## Perguntas em aberto (decidir no design.md)

- Biblioteca/abordagem de logging estruturado: `logstash-logback-encoder`
  (formato Logstash JSON) ou o *structured logging* nativo do Spring Boot
  4.x (`logging.structured.format.console`)?
- Onde gerar e propagar o `requestId`: filtro servlet próprio ou o
  `TraceId`/`SpanId` que o Micrometer Tracing já geraria automaticamente (mesmo
  sem exportar para um backend de tracing)?
- Como instrumentar a duração de cada etapa do pipeline RAG sem poluir os use
  cases com código de métrica: `Timer` manual, `@Timed` (AOP), ou decorator
  explícito?
- Nomenclatura das métricas customizadas (convenção de nome/tags do
  Micrometer, ex.: `finrag.rag.stage.duration` com tag `stage=embedding|search|llm`)?
- `/actuator/prometheus` fica público (como `/actuator/health`) ou exige
  autenticação/rede interna?
- Nível de log por ambiente: `DEBUG` em dev, `INFO` em prod — configurar
  agora ou deixar para M6 (hardening)?

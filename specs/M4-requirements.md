
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

1. Toda requisição HTTP recebe um `requestId` (gerado se o cliente não enviar
   um), propagado via MDC para todos os logs emitidos durante aquela
   requisição, em qualquer camada.
2. Logs são emitidos em formato estruturado (JSON), não texto livre — parseável
   por ferramenta de log (ex.: `jq`, ingestão em ELK/Datadog) sem regex.
3. O pipeline de pergunta (`POST /questions`) loga a duração de cada etapa
   (embedding da pergunta, busca vetorial, chamada ao LLM) e a duração total.
4. Métricas customizadas via Micrometer expõem, no mínimo: latência (timer)
   de cada etapa do pipeline RAG e do pipeline de ingestão; contagem de tokens
   de prompt/completion consumidos por chamada ao Claude; contagem de erros
   por provedor externo (OpenAI, Anthropic), com tag de tipo de erro.
5. `GET /actuator/prometheus` expõe as métricas em formato Prometheus.
6. Dados sensíveis (senha, token JWT, chave de API, corpo completo de
   pergunta/resposta do usuário) nunca aparecem em log, nem truncados — apenas
   metadados (tamanho, IDs, contagens).
7. Erros não tratados continuam retornando o `ProblemDetail` já existente
   (M1/M3) e adicionalmente são logados com stacktrace + `requestId`.
8. Testes de integração cobrem: `requestId` presente na resposta/log de uma
   requisição real, métrica de tokens incrementando após uma pergunta
   respondida com sucesso, métrica de erro incrementando quando o provedor
   externo falha.

## Fora de escopo neste marco

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

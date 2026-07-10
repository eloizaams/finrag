# M3 — Q&A RAG — Design

## Conceitos de IA deste marco (para orientar a explicação antes do código)

- **Busca semântica / similaridade de cosseno**: em vez de buscar por
  palavras-chave, comparamos o vetor (embedding) da pergunta com os vetores
  dos chunks já indexados. A similaridade de cosseno mede o ângulo entre
  dois vetores — quanto mais próximos de 0°, mais parecido o significado.
  O pgvector expõe o operador `<=>` que calcula **distância** de cosseno
  (`1 - similaridade`); ordenar por essa distância crescente é o mesmo que
  ordenar por similaridade decrescente.
- **Top-k retrieval**: dos milhares de chunks possíveis, só os `k` mais
  similares à pergunta entram no prompt — dá contexto suficiente ao LLM sem
  estourar o limite de tokens nem incluir ruído.
- **Prompt de RAG**: o texto enviado ao LLM combina uma instrução fixa
  (system prompt) com o conteúdo recuperado (contexto) e a pergunta do
  usuário. A instrução existe para reduzir alucinação — pedir
  explicitamente que o modelo só responda com base no que foi fornecido.

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Busca vetorial no JPA | Query nativa (`@Query(nativeQuery = true)` ou `EntityManager.createNativeQuery`) usando o operador `<=>` do pgvector, `JOIN chunks c ON c.document_id = d.id`, `WHERE d.user_id = :userId`, `ORDER BY c.embedding <=> :queryEmbedding`, `LIMIT :k` | `hibernate-vector` (já presente desde o M2) mapeia a coluna `vector(1536)` para leitura/escrita, mas o Hibernate não tem uma function JPQL para o operador `<=>` — query nativa é a forma direta e defensável de expressar "ordene pela distância vetorial". O filtro por `user_id` acontece na própria query, não em memória depois |
| Port de busca | `domain/port/ChunkSearchRepository.kt` — `findMostSimilar(userId: UUID, queryEmbedding: List<Float>, k: Int): List<ScoredChunk>` | Previsto desde o M2-design ("M3 cria o port de busca"). Devolve já ordenado por similaridade decrescente; a similaridade (`1 - distância`) é calculada na própria query e populada em `ScoredChunk.similarity` |
| Índice vetorial | Migration `V4`: `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)` em `chunks` | HNSW em vez de IVFFlat: não exige um passo de treino/`ANALYZE` proporcional ao volume de dados existente (IVFFlat com poucos vetores gera clusters ruins), tem boa recall em buscas aproximadas mesmo com a base ainda pequena e crescendo aos poucos — cenário típico de portfólio/MVP. IVFFlat compensaria em bases muito grandes e estáveis, o que não é o caso aqui |
| top-k | `k = 5`, configurável via `finrag.rag.top-k` | Valor comum na literatura de RAG para perguntas pontuais; grande o suficiente para cobrir contexto espalhado em 2-3 chunks, pequeno o suficiente para não estourar o prompt |
| Threshold de similaridade | Nenhum na v1 — sempre retorna os top-k, mesmo que pouco similares | Um threshold correto exigiria um dataset de avaliação para calibrar (candidato a M9, ADR já registra isso no M2). Sem um número validado, um threshold arbitrário rejeitaria contexto útil ou deixaria passar ruído do mesmo jeito. O prompt já instrui o modelo a admitir quando o contexto não tem a resposta — essa é a defesa contra chunks pouco relevantes na v1 |
| Cliente Anthropic | `RestClient` do Spring, escrito na mão, em `infrastructure/anthropic/AnthropicLlmClient.kt` | Coerente com ADR-04 (pipeline manual) e com a decisão já tomada para o cliente OpenAI no M2: dá para explicar request, response e tratamento de erro linha a linha, sem esconder o HTTP atrás de um SDK |
| Endpoint e modelo Anthropic | `POST https://api.anthropic.com/v1/messages`, headers `x-api-key` + `anthropic-version: 2023-06-01`, `model: claude-haiku-4-5`, `max_tokens: 1024`, sem streaming | Claude Haiku é a escolha do ADR-03 (barato e rápido, suficiente para Q&A com contexto curto). `max_tokens: 1024` cobre respostas explicativas de um parágrafo a alguns parágrafos sem custo desnecessário |
| Prompt de RAG | `domain/service/RagPromptBuilder.kt` — classe pura (`String, List<ScoredChunk> -> RagPrompt(system, user)`) | Mesmo raciocínio do `TextChunker` no M2: é lógica pura sem dependência externa, então vive no domínio e é testável sem mock nenhum. `RagPrompt` é um record simples com `system: String` e `user: String` |
| Formato do prompt | System: instrução fixa em PT-BR pedindo para responder só com base no contexto fornecido e dizer explicitamente "não sei" quando o contexto não cobrir a pergunta. User: pergunta + chunks numerados, cada um prefixado com o `filename` de origem | Numerar os chunks e citar o `filename` permite ao modelo (e à resposta final) referenciar a fonte; fica fácil de defender em entrevista: "o prompt injeta o contexto recuperado e restringe o modelo a ele" |
| LLM sem contexto | Quando a busca não retorna nenhum chunk, o use case **não chama o LLM** — devolve direto uma resposta padrão fixa (`"Não há documentos indexados para responder a essa pergunta."`) | Evita gastar uma chamada de LLM para um caso já conhecido (usuário sem documentos ou pergunta totalmente fora do domínio indexado); resposta determinística é mais previsível para o critério 4 |
| Mapeamento no JPA da entrada `queryEmbedding` | Reaproveita o mesmo padrão do `ChunkEntity` — a query nativa recebe o vetor serializado como string `"[0.1,0.2,...]"` (formato textual aceito pelo pgvector) via parâmetro `:queryEmbedding` castado para `vector` na própria SQL | Evita introduzir um tipo Hibernate customizado só para o parâmetro de busca; o cast (`CAST(:queryEmbedding AS vector)`) é explícito na query nativa |
| Use case | `application/AskQuestionUseCase.kt`: `EmbeddingProvider.embed(listOf(question))` → `ChunkSearchRepository.findMostSimilar(userId, embedding, k)` → se vazio, resposta padrão; senão `RagPromptBuilder.build(question, chunks)` → `LlmClient.generate(prompt)` → monta `Answer(text, sources)` | Mesma forma de orquestração do `IngestDocumentUseCase` (M2): o use case não conhece nenhum detalhe de OpenAI, pgvector ou Anthropic, só os ports |
| Exceções de domínio | `BlankQuestionException` (→ `400`), `LlmProviderException` (→ `502`) | `EmbeddingProviderException` já existe do M2 e é reaproveitada para falha de embedding da pergunta (mesmo `502`) |
| API | `api/QuestionController.kt` (`POST /questions`), DTOs `QuestionRequest { question: String }`, `AnswerResponse { answer: String, sources: List<SourceResponse> }`, `SourceResponse { documentId, filename, excerpt, similarity }` | `userId` lido do `SecurityContext` via `@AuthenticationPrincipal`, igual ao `DocumentController` |
| Exception handling | `api/QuestionExceptionHandler.kt` (`@RestControllerAdvice`) → `ProblemDetail`: `400` para `BlankQuestionException`, `502` para `LlmProviderException` e `EmbeddingProviderException` | Reaproveita o padrão de `DocumentExceptionHandler`; atenção ao `@Order(HIGHEST_PRECEDENCE)` (lição aprendida no M2) |
| Formato de erro | `ProblemDetail` (RFC 7807) | Mesma decisão do M1/M2 |
| Estratégia de teste | Fake de `EmbeddingProvider` (já existe, reaproveitado) + fake de `LlmClient` via `@TestConfiguration` (resposta determinística; modo de falha simulável) nos testes de integração; busca vetorial testada de verdade no Testcontainers com embeddings pequenos e determinísticos construídos à mão (ex.: vetores ortogonais/próximos conhecidos) para validar ordenação; `AnthropicLlmClient` ganha teste isolado com `MockRestServiceServer` | Mesma estratégia validada no M2: fakes mantêm os testes de integração rápidos e determinísticos; o adapter HTTP real ganha cobertura própria sem chamar a API de verdade |

## Onde entra cada peça (Clean Architecture)

```
domain/
├── model/
│   ├── Answer.kt                    # text, sources: List<Source>
│   ├── Source.kt                    # documentId, filename, excerpt, similarity
│   └── ScoredChunk.kt               # chunkId, documentId, filename, content, similarity
├── service/
│   └── RagPromptBuilder.kt          # build(question, chunks) -> RagPrompt(system, user)
└── port/
    ├── ChunkSearchRepository.kt     # findMostSimilar(userId, queryEmbedding, k)
    └── LlmClient.kt                 # generate(systemPrompt, userPrompt) -> String

domain (exceções):
    BlankQuestionException           # -> 400
    LlmProviderException             # -> 502
    (EmbeddingProviderException já existe, do M2, reaproveitada)

application/
└── AskQuestionUseCase.kt            # orquestra: embedding -> busca -> prompt -> LLM -> Answer

infrastructure/
├── persistence/
│   └── ChunkSearchRepositoryJpaAdapter.kt  # implementa ChunkSearchRepository; query nativa <=>
└── anthropic/
    ├── AnthropicProperties.kt       # api-key, base-url, model, max-tokens (ConfigurationProperties)
    └── AnthropicLlmClient.kt        # implementa LlmClient; RestClient, POST /v1/messages

api/
├── QuestionController.kt            # POST /questions
├── dto/
│   ├── QuestionRequest.kt           # question
│   └── AnswerResponse.kt            # answer, sources: List<SourceResponse>
└── QuestionExceptionHandler.kt      # @RestControllerAdvice -> ProblemDetail p/ 400 e 502
```

**Por que `ChunkSearchRepository` e `LlmClient` são ports**: a busca vetorial
depende de SQL específico do pgvector e a geração de resposta depende da API
Anthropic — o use case não pode conhecer nenhum dos dois (regra de
dependência), e os testes precisam trocá-los por fakes. O `RagPromptBuilder`
é lógica pura (strings → strings), sem dependência externa: interface aqui
seria cerimônia, mesmo raciocínio do `TextChunker` no M2.

## Fluxo de consulta (`POST /questions`)

1. `QuestionController` recebe `{ "question": "..." }`, lê o `userId` do
   `SecurityContext` (populado pelo filtro JWT do M1) e repassa ao use case
2. `AskQuestionUseCase` valida a pergunta — em branco/vazia lança
   `BlankQuestionException` → `400`
3. `EmbeddingProvider.embed(listOf(question))` gera o embedding da pergunta;
   falha (timeout, 401, 5xx da OpenAI) lança `EmbeddingProviderException`
   → `502`
4. `ChunkSearchRepository.findMostSimilar(userId, embedding, k)` busca os
   `k` chunks mais similares, restritos aos documentos do usuário
5. Se a busca não retornar nenhum chunk, o use case devolve direto
   `Answer("Não há documentos indexados para responder a essa pergunta.", emptyList())`
   — sem chamar o LLM
6. Caso contrário, `RagPromptBuilder.build(question, chunks)` monta o
   `RagPrompt` (system + user)
7. `LlmClient.generate(prompt.system, prompt.user)` chama a Anthropic; falha
   (timeout, 401, 5xx) lança `LlmProviderException` → `502`
8. `AskQuestionUseCase` monta `Answer(text, sources)`, onde cada `Source` vem
   de um `ScoredChunk` usado no contexto
9. Controller retorna `200` com `AnswerResponse { answer, sources }`

## Algoritmo de busca (referência para implementação)

1. Serializar o embedding da pergunta (`List<Float>`) no formato textual do
   pgvector: `"[0.123,0.456,...]"`
2. Query nativa: `SELECT c.id, c.document_id, d.filename, c.content, 1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity FROM chunks c JOIN documents d ON d.id = c.document_id WHERE d.user_id = :userId ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector) LIMIT :k`
3. Mapear cada linha para `ScoredChunk(chunkId, documentId, filename, content, similarity)`
4. Retornar a lista ordenada por `similarity` decrescente (a própria query já
   ordena; nenhum reordenamento em memória é necessário)

## Migration Flyway (V4)

Próximo número livre: `V4__create_chunks_embedding_index.sql`.

```sql
CREATE INDEX idx_chunks_embedding_hnsw
    ON chunks
    USING hnsw (embedding vector_cosine_ops);
```

Índice adiado do M2 (decisão registrada lá: "índice agora é otimização de
algo que não existe"). Agora a query de busca existe e justifica o índice.
`vector_cosine_ops` é a classe de operador coerente com o `<=>` usado na
query — o mesmo espaço de distância tem que ser usado no índice e na busca,
senão o índice não é considerado pelo planner.

## Dependências novas (build.gradle.kts)

Nenhuma. `spring-boot-restclient` já está em `implementation` desde o M2
(usado pelo `OpenAiEmbeddingProvider`) — o `AnthropicLlmClient` reutiliza o
mesmo `RestClient.Builder`. `MockRestServiceServer` já vem via
`spring-test`/`spring-boot-resttestclient`, também já presentes.

## Configuração (application.yaml)

```yaml
finrag:
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}                 # obrigatório via env var, sem default
    base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}
```

`model` (`claude-haiku-4-5`), `max-tokens` (`1024`), `top-k` (`5`) e
`min-similarity` (`0.25`) têm default no código (`AnthropicProperties` /
`RagProperties`) e só entram no yaml quando se quer sobrescrever — uma única
fonte de verdade para cada valor (ajuste pós-code-review, ver seção abaixo).

- `ANTHROPIC_API_KEY` segue a mesma regra do `OPENAI_API_KEY` (M2) e do
  `JWT_SECRET` (M1): env var obrigatória, nunca commitada, nunca logada —
  mesmo cuidado ao tratar erro da Anthropic para não logar headers da
  requisição
- `base-url` sobrescrevível permite `MockRestServiceServer` nos testes do
  adapter e apontar para um servidor local em testes manuais, sem tocar no
  código
- Adicionar `ANTHROPIC_API_KEY` ao `environment` do serviço `app` no
  `docker-compose.yml`, ao lado de `OPENAI_API_KEY`

## Trade-offs conscientes

- ~~**Sem threshold de similaridade**~~ *(revertido no pós-code-review, ver
  seção abaixo)*: a v1 original não cortava por similaridade — a defesa era
  só o prompt. O code review mostrou que sem corte o ramo "sem contexto" era
  inalcançável para qualquer usuário com documentos, e chunks irrelevantes
  iam sempre pro LLM. Entrou `min-similarity: 0.25` (conservador,
  configurável); a calibração fina com golden dataset continua sendo M9.
- **Sem re-ranking**: os top-k chunks retornados pela busca vetorial vão
  direto pro prompt, sem uma segunda passada de reordenação por relevância.
  ADR-04/CLAUDE.md já colocam re-ranking fora de escopo da v1.
- **Prompt não versionado/testado com golden dataset**: o texto do system
  prompt é uma primeira versão razoável, não validada contra um conjunto de
  perguntas de referência. Ajustável sem mudar contrato de API.
- **Uma chamada de embedding + uma chamada de LLM por pergunta**: sem
  cache de perguntas repetidas nem de embeddings. Custo baixo dado o
  tamanho esperado de uso (portfólio), mas é a primeira coisa a otimizar se
  o volume crescer.
- **`HNSW` sem `m`/`ef_construction` customizados**: usa os valores padrão
  do pgvector. Ajuste fino de parâmetros do índice é otimização prematura
  sem um volume de dados real para medir contra.

## Ajustes pós-code-review (2026-07-10)

Um code review do marco completo levou a estas mudanças em relação às
decisões acima (o histórico original foi mantido para registrar a evolução):

- **Threshold de similaridade**: adicionado `finrag.rag.min-similarity`
  (default `0.25` em `RagProperties`). Motivo: sem corte, `chunks.isEmpty()`
  só era verdadeiro com corpus vazio — a mensagem padrão prometida pelo
  README para "nenhum resultado relevante" era inalcançável e chunks
  irrelevantes iam sempre pro LLM.
- **Validação da pergunta**: `BlankQuestionException` e o check no use case
  foram substituídos por `@field:NotBlank` + `@Valid`, o mesmo mecanismo de
  Bean Validation do M1 — o 400 de `/questions` agora tem o mesmo formato
  (array `errors`) dos endpoints de auth.
- **Exception handling**: `QuestionExceptionHandler` foi removido; as falhas
  de provedor (`EmbeddingProviderException`, `LlmProviderException` → `502`)
  moraram num `ProviderExceptionHandler` global, eliminando o handler
  duplicado entre `/documents` e `/questions`.
- **Modelo `Source` removido**: `Answer` carrega `List<ScoredChunk>` direto
  e `SourceResponse.from(ScoredChunk)` mapeia pro DTO — eram três cópias
  idênticas dos mesmos campos. `excerpt` agora é de fato um trecho (até 200
  chars), não o chunk inteiro.
- **`stop_reason`**: `AnthropicLlmClient` loga warning quando a resposta é
  truncada por `max_tokens` (antes o truncamento era silencioso).
- **Prompt com contexto delimitado**: chunks agora vão dentro de tags
  `<documento arquivo="...">` e o system prompt instrui a tratar esse
  conteúdo como dados — mitigação de prompt injection via documento enviado.
- **`user_id` desnormalizado em `chunks`** (migration `V5`): o filtro de
  tenant ficava na tabela JOINada, fora do alcance do índice HNSW — em corpus
  multi-usuário grande isso degrada recall (candidatos ANN globais filtrados
  depois) ou derruba a query para scan sequencial.
- **Fonte única de configuração**: defaults de `model`/`max-tokens`/`top-k`/
  `min-similarity` vivem só no código; o yaml carrega apenas o que vem de
  env var.

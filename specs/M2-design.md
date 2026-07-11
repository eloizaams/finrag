# M2 — Ingestão de documentos — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Extração de PDF | PDFBox 3.x (`PDFTextStripper`) | Padrão de mercado na JVM, mantido pela Apache, API direta para PDFs com camada de texto. Alternativa (Tika) puxa dezenas de dependências transitivas e esconde o funcionamento — pior para defender em entrevista |
| Extração de Markdown | Texto puro (`String(bytes)`) | A marcação Markdown (`#`, `*`, `-`) é leve e não degrada o embedding de forma mensurável; parsear com commonmark seria dependência e código a mais sem ganho. Trade-off consciente documentado abaixo |
| Estratégia de chunking | Parágrafos agregados até ~1000 caracteres, overlap de ~200 | Quebra por parágrafo (linha em branco) preserva unidades de sentido; agregar parágrafos consecutivos até o limite evita chunks minúsculos; parágrafo maior que o limite é fatiado. Overlap evita perder informação que cai na fronteira entre chunks. Medir em caracteres dispensa tokenizer (jtokkit): para `text-embedding-3-small` (limite 8191 tokens), 1000 chars ≈ 250–300 tokens — folga enorme, precisão de tokens não compraria nada. Valores configuráveis no yaml |
| Onde vive o chunking | `domain/service/TextChunker.kt` — classe concreta, sem port | É algoritmo puro (String → List<String>), sem dependência externa — criar interface para isso seria abstração sem segundo implementador. O `00-architecture.md` agrupa chunking em `infrastructure/parsing` na visão de pipeline, mas pela regra de dependência lógica pura pertence ao domínio: testável sem mock nenhum |
| Cliente OpenAI | `RestClient` do Spring, escrito na mão | Coerente com o ADR-04 (pipeline manual): dá para explicar request, response e tratamento de erro linha a linha. SDK oficial adicionaria dependência grande e esconderia o HTTP |
| Chamada de embeddings | Em lote — todos os chunks do documento numa requisição | A API `/v1/embeddings` aceita array de inputs (até 2048). Um documento de 50 chunks vira 1 requisição em vez de 50: menos latência, menos rate limit, tratamento de erro num ponto só |
| Mapeamento do vetor no JPA | `hibernate-vector` — `float[]` com `@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length = 1536)` | Suporte nativo do Hibernate (6.4+) a pgvector; a entidade `ChunkEntity` fica idiomática com o resto da persistência JPA do projeto (padrão do M1), sem SQL cru para inserir |
| Índice vetorial | **Não** criar no M2 | Ninguém consulta por similaridade neste marco — índice agora é otimização de algo que não existe. Com poucos milhares de vetores, scan sequencial resolve. O M3 cria o índice na migration dele, junto da decisão HNSW vs IVFFlat, justificada pela query real |
| Limite de upload | 10 MB via `spring.servlet.multipart.max-file-size` | Cobre relatórios financeiros típicos sem permitir uploads que custem caro em embeddings nem estourar memória (ingestão síncrona carrega o arquivo inteiro). O Spring lança `MaxUploadSizeExceededException` sozinho — só mapear para `413` |
| Atomicidade | Embeddings **antes** da transação; persistência de documento + chunks numa transação única | Se a OpenAI falhar, nada foi escrito no banco (critério 7). Envolver a chamada HTTP externa num `@Transactional` prenderia uma conexão do pool durante toda a chamada — anti-pattern clássico que esgota o pool sob carga |
| Fronteira transacional | `DocumentRepository.save(document, chunks)` — um método de port que persiste tudo, adapter anotado `@Transactional` | Os use cases não têm anotação Spring (padrão do M1), então a transação não pode ser demarcada na application. Um único método de port que salva documento + chunks torna a atomicidade parte do **contrato** da porta, não um detalhe de quem chama. Dispensa `ChunkRepository` separado neste marco (M3 cria o port de busca) |
| Estratégia de teste | Fake do port `EmbeddingProvider` nos testes de integração + teste isolado do adapter HTTP com `MockRestServiceServer` | O fake (vetor determinístico derivado do texto) mantém os testes de integração rápidos/estáveis e simula falha para o critério 7. O adapter real ganha teste próprio validando request, parse da resposta e erro. `MockRestServiceServer` cumpre o papel do MockWebServer/WireMock combinado, mas já vem no `spring-test` — zero dependência nova e integração nativa com `RestClient` |
| Detecção do tipo de arquivo | Extensão do filename (`.pdf`, `.md`, `.markdown`) → enum `DocumentType` | Content-Type de multipart é informado pelo cliente e frequentemente genérico (`application/octet-stream`); extensão é suficiente para dois formatos. Extensão desconhecida → `415` |
| Geração de UUID | Na aplicação (`UUID.randomUUID()`) | Mesma decisão do M1 (nota na migration V2): evita depender da extensão `pgcrypto` do Postgres |
| Formato de erro | `ProblemDetail` (RFC 7807) | Reaproveita o padrão estabelecido no M1 |

## Onde entra cada peça (Clean Architecture)

```
domain/
├── model/
│   ├── Document.kt                  # id, userId, filename, chunkCount, createdAt
│   ├── Chunk.kt                     # id, documentId, index, content, embedding (FloatArray)
│   └── DocumentType.kt              # enum PDF/MARKDOWN + fromFilename() (lança se desconhecida)
├── service/
│   └── TextChunker.kt               # algoritmo puro: parágrafos agregados + overlap
└── port/
    ├── DocumentRepository.kt        # interface: save(document, chunks) atômico, findAllByUserId
    ├── TextExtractor.kt             # interface: extract(bytes, type) -> String
    └── EmbeddingProvider.kt         # interface: embed(texts: List<String>) -> List<FloatArray>

domain (exceções):
    UnsupportedDocumentTypeException # -> 415
    EmptyDocumentException           # -> 422
    EmbeddingProviderException       # -> 502

application/
├── IngestDocumentUseCase.kt         # orquestra: tipo -> extração -> chunking -> embeddings -> persistência
└── ListDocumentsUseCase.kt          # findAllByUserId, só metadados

infrastructure/
├── parsing/
│   └── DefaultTextExtractor.kt      # implementa TextExtractor: PDF via PDFBox, Markdown via String(bytes)
├── persistence/
│   ├── DocumentEntity.kt            # entidade JPA
│   ├── ChunkEntity.kt               # entidade JPA; embedding float[] @JdbcTypeCode(SqlTypes.VECTOR)
│   └── DocumentRepositoryJpaAdapter.kt  # implementa DocumentRepository; save é @Transactional
└── openai/
    ├── OpenAiProperties.kt          # api-key, base-url, embedding-model (ConfigurationProperties)
    └── OpenAiEmbeddingProvider.kt   # implementa EmbeddingProvider; RestClient, POST /v1/embeddings em lote

api/
├── DocumentController.kt            # POST /documents (multipart), GET /documents
├── dto/
│   └── DocumentResponse.kt          # id, filename, chunkCount, createdAt
└── DocumentExceptionHandler.kt      # @RestControllerAdvice -> ProblemDetail p/ 415, 422, 502 e 413
```

**Por que `TextExtractor` e `EmbeddingProvider` são ports mas `TextChunker` não**:
extração depende de biblioteca externa (PDFBox) e embeddings dependem de serviço
externo (OpenAI) — o use case não pode conhecer nenhum dos dois (regra de
dependência), e os testes precisam trocá-los por fakes. O chunker é lógica pura
sem dependência: interface aqui seria cerimônia.

## Fluxo de ingestão (`POST /documents`)

1. Requisição acima de 10 MB nem chega ao controller: o Spring rejeita e o
   handler mapeia `MaxUploadSizeExceededException` → `413` com `ProblemDetail`
2. `DocumentController` recebe o multipart, lê o `userId` do `SecurityContext`
   (populado pelo filtro JWT do M1) e repassa `filename` + bytes ao use case
3. `IngestDocumentUseCase` deriva `DocumentType.fromFilename(filename)` —
   extensão desconhecida lança `UnsupportedDocumentTypeException` → `415`
4. `TextExtractor.extract(bytes, type)` devolve o texto; texto em branco lança
   `EmptyDocumentException` → `422` (nada foi persistido ainda)
5. `TextChunker.chunk(texto)` produz a lista de trechos (~1000 chars, overlap ~200)
6. `EmbeddingProvider.embed(trechos)` — **fora de qualquer transação** — chama a
   OpenAI em lote; falha (timeout, 401, 5xx) lança
   `EmbeddingProviderException` → `502`, e nada foi escrito no banco
7. Monta `Document` + lista de `Chunk` (UUIDs gerados na aplicação) e chama
   `DocumentRepository.save(document, chunks)` — transação única no adapter:
   ou persiste tudo, ou nada
8. Controller retorna `201` com `DocumentResponse { id, filename, chunkCount,
   createdAt }`

## Fluxo de listagem (`GET /documents`)

1. `DocumentController` lê o `userId` do `SecurityContext`
2. `ListDocumentsUseCase` chama `DocumentRepository.findAllByUserId(userId)`
3. Retorna `200` com lista de `DocumentResponse` (metadados apenas — chunks e
   embeddings nunca saem pela API)

## Algoritmo de chunking (referência para implementação)

1. Normalizar quebras de linha e dividir o texto em parágrafos (separador:
   linha em branco), descartando parágrafos vazios
2. Acumular parágrafos consecutivos num buffer enquanto o total ≤ `max-chars`
   (1000); ao estourar, fechar o chunk atual e começar o próximo
3. Parágrafo sozinho maior que `max-chars` é fatiado em janelas de `max-chars`
4. Cada chunk (exceto o primeiro) começa com os últimos `overlap-chars` (200)
   do chunk anterior
5. Saída: lista ordenada de strings; o índice na lista vira `chunk_index`

## Migration Flyway (V3)

Próximo número livre: `V3__create_documents_and_chunks_tables.sql`
(V1 = pgvector, V2 = users). UUIDs gerados na aplicação, como no M1.

```sql
CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    filename    VARCHAR(255) NOT NULL,
    chunk_count INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_user_id ON documents (user_id);

CREATE TABLE chunks (
    id          UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536) NOT NULL
);

CREATE INDEX idx_chunks_document_id ON chunks (document_id);
```

Sem índice vetorial em `embedding` — decisão registrada acima, fica para o M3.

## Dependências novas (build.gradle.kts)

- `org.apache.pdfbox:pdfbox:3.0.x` — extração de texto de PDF
- `org.hibernate.orm:hibernate-vector` — mapeamento de `vector(1536)` (versão
  gerenciada pelo BOM do Spring Boot, junto do hibernate-core)
- `org.springframework.boot:spring-boot-restclient` — promover de
  `testImplementation` (já presente) para `implementation`: o
  `OpenAiEmbeddingProvider` usa `RestClient` em código de produção
- Testes: nada novo — `MockRestServiceServer` já vem via `spring-test` /
  `spring-boot-resttestclient`

## Configuração (application.yaml)

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB   # folga p/ overhead do multipart além do arquivo

finrag:
  openai:
    api-key: ${OPENAI_API_KEY}              # obrigatório via env var, sem default
    base-url: ${OPENAI_BASE_URL:https://api.openai.com}
    embedding-model: text-embedding-3-small
  chunking:
    max-chars: 1000
    overlap-chars: 200
```

- `OPENAI_API_KEY` segue a mesma regra do `JWT_SECRET` (M1): env var
  obrigatória, nunca commitada, nunca logada — cuidado especial para não logar
  headers da requisição ao tratar erro da OpenAI
- `base-url` sobrescrevível permite apontar para um servidor local em testes
  manuais, sem tocar no código
- Adicionar `OPENAI_API_KEY` ao `environment` do serviço da API no
  `docker-compose.yml`

## Trade-offs conscientes

- **Ingestão síncrona**: um PDF grande segura a requisição por segundos
  (extração + chamada de embeddings). É o ADR-05 — aceitável no MVP; fila
  assíncrona é candidata ao M9.
- **Embeddings antes do commit**: se a OpenAI responder e o commit falhar em
  seguida (queda do banco), os embeddings foram pagos e perdidos. Janela
  minúscula e custo desprezível — muito melhor que o inverso (documento
  persistido pela metade) ou que segurar transação durante HTTP externo.
- **Chunking por caracteres, valores por heurística**: 1000/200 não foi
  validado com dataset de avaliação (golden dataset é candidato a M9); são
  valores razoáveis da literatura, configuráveis para ajuste posterior.
- **Markdown com marcação nos chunks**: `#` e `*` entram no embedding. Ruído
  pequeno; se a busca do M3 sofrer, trocar por commonmark é mudança isolada
  no `DefaultTextExtractor`.
- **Detecção por extensão de arquivo**: um `.pdf` renomeado de outro formato
  passa da checagem e falha na extração (PDFBox lança) — cai como `422`, não
  `500`; aceitável sem sniffing de magic bytes.
- **Sem dedução de custo/limite por usuário**: qualquer usuário autenticado
  pode ingerir à vontade dentro do limite de 10 MB por arquivo. Rate limiting
  ficou fora de escopo desde o M1.

# M2 — Ingestão de documentos — Tasks

## Domínio e portas

- [ ] Criar `domain/model/Document.kt` e `domain/model/Chunk.kt`
- [ ] Criar `domain/model/DocumentType.kt` (enum PDF/MARKDOWN,
      `fromFilename()` lançando exceção para extensão desconhecida)
- [ ] Criar exceções de domínio: `UnsupportedDocumentTypeException`,
      `EmptyDocumentException`, `EmbeddingProviderException`
- [ ] Criar `domain/port/DocumentRepository.kt` (interface:
      `save(document, chunks)` atômico, `findAllByUserId`)
- [ ] Criar `domain/port/TextExtractor.kt` (interface: `extract(bytes, type)`)
- [ ] Criar `domain/port/EmbeddingProvider.kt` (interface: `embed(texts)`)
- [ ] Implementar `domain/service/TextChunker.kt` (parágrafos agregados até
      `max-chars`, overlap, fatiamento de parágrafo gigante) + teste unitário
      puro: texto menor que um chunk, múltiplos parágrafos agregados,
      parágrafo maior que o limite, overlap presente entre chunks vizinhos,
      texto vazio → lista vazia

## Casos de uso

- [ ] Implementar `application/IngestDocumentUseCase.kt` (+ teste unitário com
      fakes de `TextExtractor`/`EmbeddingProvider`/`DocumentRepository`:
      fluxo feliz, extensão não suportada, texto em branco, falha de
      embeddings não chama o repositório)
- [ ] Implementar `application/ListDocumentsUseCase.kt` (+ teste unitário com
      fake de `DocumentRepository`)

## Infraestrutura — parsing

- [ ] Adicionar dependência `org.apache.pdfbox:pdfbox:3.0.x`
- [ ] Implementar `infrastructure/parsing/DefaultTextExtractor.kt` (PDF via
      `PDFTextStripper`, Markdown via `String(bytes)`) + teste unitário com
      um PDF pequeno e um Markdown de fixture em `src/test/resources`

## Infraestrutura — persistência

- [ ] Adicionar dependência `org.hibernate.orm:hibernate-vector`
- [ ] Criar migration `V3__create_documents_and_chunks_tables.sql` (tabelas
      `documents` e `chunks` conforme design; sem índice vetorial)
- [ ] Criar `infrastructure/persistence/DocumentEntity.kt` e `ChunkEntity.kt`
      (embedding `float[]` com `@JdbcTypeCode(SqlTypes.VECTOR)` +
      `@Array(length = 1536)`)
- [ ] Implementar `infrastructure/persistence/DocumentRepositoryJpaAdapter.kt`
      com `save` transacional (documento + chunks: tudo ou nada)
- [ ] Teste de integração (Testcontainers) do adapter: salvar documento com
      chunks e ler de volta (embedding com 1536 dimensões preservado),
      `findAllByUserId` filtra por usuário, falha no meio do save não deixa
      linhas órfãs

## Infraestrutura — OpenAI

- [ ] Promover `spring-boot-restclient` de `testImplementation` para
      `implementation`
- [ ] Criar `infrastructure/openai/OpenAiProperties.kt`
      (`@ConfigurationProperties`: `api-key`, `base-url`, `embedding-model`)
- [ ] Configurar `finrag.openai.*` e `finrag.chunking.*` no `application.yaml`
      (chave via `OPENAI_API_KEY`, sem default)
- [ ] Implementar `infrastructure/openai/OpenAiEmbeddingProvider.kt`
      (`RestClient`, `POST /v1/embeddings` em lote, mapear falhas para
      `EmbeddingProviderException` sem logar a chave nem headers)
- [ ] Teste do adapter com `MockRestServiceServer`: request correto (modelo,
      inputs em lote, header Authorization), parse da resposta na ordem dos
      inputs, erro HTTP → `EmbeddingProviderException`

## API

- [ ] Configurar `spring.servlet.multipart.max-file-size: 10MB` (e
      `max-request-size: 11MB`)
- [ ] Criar DTO `DocumentResponse` (id, filename, chunkCount, createdAt)
- [ ] Implementar `api/DocumentController.kt` (`POST /documents` multipart +
      `GET /documents`, `userId` lido do `SecurityContext`)
- [ ] Implementar `api/DocumentExceptionHandler.kt` (`@RestControllerAdvice` →
      `ProblemDetail`: `415`, `422`, `502` e `MaxUploadSizeExceededException`
      → `413`)
- [ ] Registrar os novos use cases no `UseCaseConfig` (composition root)
- [ ] Adicionar `OPENAI_API_KEY` ao serviço da API no `docker-compose.yml`

## Testes de integração (Kotest + Testcontainers)

> Fake de `EmbeddingProvider` via `@TestConfiguration` (vetor determinístico
> derivado do texto; modo de falha para o cenário de erro) — OpenAI real nunca
> é chamada nos testes.

- [ ] Ingestão de PDF com sucesso → `201`, metadados no corpo, chunks +
      embeddings no banco batendo com o `chunkCount` retornado
- [ ] Ingestão de Markdown com sucesso → `201`
- [ ] Upload de formato não suportado (ex.: `.png`) → `415`
- [ ] Upload de arquivo vazio / sem texto extraível → `422`, nada persistido
- [ ] Fake de embeddings em modo de falha → `502`, nenhuma linha em
      `documents`/`chunks` (atomicidade, critério 7)
- [ ] `GET /documents` retorna só os documentos do usuário do token (criar
      dois usuários, cada um com documento, e conferir o isolamento)
- [ ] `POST /documents` e `GET /documents` sem token → `401`

## Fechamento do marco

- [ ] Rodar `./gradlew build` limpo (build + todos os testes)
- [ ] Atualizar README (endpoints `/documents` com exemplos de `curl` — upload
      e listagem —, env var `OPENAI_API_KEY` obrigatória)
- [ ] Validar fluxo completo via `docker compose up` com a OpenAI real: subir
      um PDF e um Markdown de verdade, conferir chunks/embeddings no banco,
      listar, e conferir os erros 415/422/413
- [ ] Commit(s) semânticos ao longo da implementação
- [ ] Abrir PR de `feature/m2-ingestao-documentos` para `develop`, CI verde

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M2-requirements.md` atendidos, testes de
integração cobrindo os fluxos listados acima, CI verde no PR.

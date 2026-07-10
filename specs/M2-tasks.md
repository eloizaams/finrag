# M2 — Ingestão de documentos — Tasks

## Domínio e portas

- [x] Criar `domain/model/Document.kt` e `domain/model/Chunk.kt`
- [x] Criar `domain/model/DocumentType.kt` (enum PDF/MARKDOWN,
      `fromFilename()` lançando exceção para extensão desconhecida)
- [x] Criar exceções de domínio: `UnsupportedDocumentTypeException`,
      `EmptyDocumentException`, `EmbeddingProviderException`
- [x] Criar `domain/port/DocumentRepository.kt` (interface:
      `save(document, chunks)` atômico, `findAllByUserId`)
- [x] Criar `domain/port/TextExtractor.kt` (interface: `extract(bytes, type)`)
- [x] Criar `domain/port/EmbeddingProvider.kt` (interface: `embed(texts)`)
- [x] Implementar `domain/service/TextChunker.kt` (parágrafos agregados até
      `max-chars`, overlap, fatiamento de parágrafo gigante) + teste unitário
      puro: texto menor que um chunk, múltiplos parágrafos agregados,
      parágrafo maior que o limite, overlap presente entre chunks vizinhos,
      texto vazio → lista vazia

## Casos de uso

- [x] Implementar `application/IngestDocumentUseCase.kt` (+ teste unitário com
      fakes de `TextExtractor`/`EmbeddingProvider`/`DocumentRepository`:
      fluxo feliz, extensão não suportada, texto em branco, falha de
      embeddings não chama o repositório)
- [x] Implementar `application/ListDocumentsUseCase.kt` (+ teste unitário com
      fake de `DocumentRepository`)

## Infraestrutura — parsing

- [x] Adicionar dependência `org.apache.pdfbox:pdfbox:3.0.7`
- [x] Implementar `infrastructure/parsing/DefaultTextExtractor.kt` (PDF via
      `PDFTextStripper`, Markdown via `String(bytes)`) + teste unitário: PDF
      gerado em memória com o próprio PDFBox (ida e volta, sem fixture
      binária versionada) e Markdown como texto puro

## Infraestrutura — persistência

- [x] Adicionar dependência `org.hibernate.orm:hibernate-vector` (versão
      resolvida pelo BOM do Spring Boot: `7.4.1.Final`, mesma do
      `hibernate-core`)
- [x] Criar migration `V3__create_documents_and_chunks_tables.sql` (tabelas
      `documents` e `chunks` conforme design; sem índice vetorial)
- [x] Criar `infrastructure/persistence/DocumentEntity.kt` e `ChunkEntity.kt`
      (embedding `float[]` com `@JdbcTypeCode(SqlTypes.VECTOR)` +
      `@Array(length = 1536)`)
- [x] Implementar `infrastructure/persistence/DocumentRepositoryJpaAdapter.kt`
      com `save` transacional (documento + chunks: tudo ou nada)
- [x] Teste de integração (Testcontainers) do adapter: salvar documento com
      chunks e ler de volta (embedding com 1536 dimensões preservado),
      `findAllByUserId` filtra por usuário, falha no meio do save não deixa
      linhas órfãs (rollback transacional)

## Infraestrutura — OpenAI

- [x] ~~Promover `spring-boot-restclient` de `testImplementation` para
      `implementation`~~ — não foi necessário: `RestClient` já vem
      transitivamente do `spring-boot-starter-web`, e `MockRestServiceServer`
      já está disponível pelos `testImplementation` existentes
- [x] Criar `infrastructure/openai/OpenAiProperties.kt`
      (`@ConfigurationProperties`: `api-key`, `base-url`, `embedding-model`;
      primeira vez usando essa anotação no projeto — registrada via
      `@ConfigurationPropertiesScan` no `FinragApplication.kt`)
- [x] Configurar `finrag.openai.*` e `finrag.chunking.*` no `application.yaml`
      (chave via `OPENAI_API_KEY`, sem default; env var de teste adicionada
      em `build.gradle.kts` para não quebrar os `@SpringBootTest` existentes)
- [x] Implementar `infrastructure/openai/OpenAiEmbeddingProvider.kt`
      (`RestClient`, `POST /v1/embeddings` em lote, mapear falhas para
      `EmbeddingProviderException` sem logar a chave nem headers)
- [x] Teste do adapter com `MockRestServiceServer`: request correto (modelo,
      inputs em lote, header Authorization), parse da resposta na ordem dos
      inputs (usando o campo `index` de forma defensiva), erro HTTP →
      `EmbeddingProviderException`

## API

- [x] Configurar `spring.servlet.multipart.max-file-size: 10MB` (e
      `max-request-size: 11MB`)
- [x] Criar DTO `DocumentResponse` (id, filename, chunkCount, createdAt)
- [x] Implementar `api/DocumentController.kt` (`POST /documents` multipart +
      `GET /documents`, `userId` lido do `SecurityContext` via
      `@AuthenticationPrincipal` — o filtro JWT já injeta o `UUID` do
      usuário como principal)
- [x] Implementar `api/DocumentExceptionHandler.kt` (`@RestControllerAdvice` →
      `ProblemDetail`: `415`, `422`, `502` e `MaxUploadSizeExceededException`
      → `413`)
- [x] Registrar os novos use cases no `UseCaseConfig` (composition root;
      `TextChunker` também virou bean, parametrizado por
      `finrag.chunking.*` via `@Value`)
- [x] Adicionar `OPENAI_API_KEY` ao serviço da API no `docker-compose.yml`

## Testes de integração (Kotest + Testcontainers)

> Fake de `EmbeddingProvider` via `@TestConfiguration` (vetor determinístico
> derivado do texto; modo de falha para o cenário de erro) — OpenAI real nunca
> é chamada nos testes.

- [x] Ingestão de PDF com sucesso → `201`, metadados no corpo, chunks +
      embeddings no banco batendo com o `chunkCount` retornado
- [x] Ingestão de Markdown com sucesso → `201`
- [x] Upload de formato não suportado (ex.: `.png`) → `415`
- [x] Upload de arquivo vazio / sem texto extraível → `422` (`HttpStatus`
      atual é `UNPROCESSABLE_CONTENT`; `UNPROCESSABLE_ENTITY` ficou
      deprecated no Spring Framework 7), nada persistido
- [x] Fake de embeddings em modo de falha → `502`, nenhuma linha em
      `documents`/`chunks` (atomicidade, critério 7)
- [x] `GET /documents` retorna só os documentos do usuário do token (criar
      dois usuários, cada um com documento, e conferir o isolamento)
- [x] `POST /documents` e `GET /documents` sem token → `401`

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

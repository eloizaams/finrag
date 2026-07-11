# M5 — Gestão de documentos — Tasks

## Domínio e portas

- [ ] Criar `domain/model/PageResult.kt` (genérico: `items`, `page`, `size`,
      `totalItems`, `totalPages` derivado)
- [ ] Criar `domain/exception/DocumentNotFoundException.kt`
- [ ] Estender `domain/port/DocumentRepository.kt`: `findByIdAndUserId`,
      `deleteByIdAndUserId`, `findAllByUserId(userId, page, size): PageResult<Document>`

## Casos de uso

- [ ] Implementar `application/GetDocumentUseCase.kt` (+ teste unitário com
      fake: encontrado, não encontrado, documento de outro usuário)
- [ ] Implementar `application/DeleteDocumentUseCase.kt` (+ teste unitário com
      fake: deletado, inexistente → exceção)
- [ ] Alterar `application/ListDocumentsUseCase.kt` para receber `page`/`size`
      e retornar `PageResult<Document>` (+ atualizar teste unitário)
- [ ] Atualizar `FakeDocumentAdapters.kt` com os métodos novos da porta

## Infraestrutura — persistência

- [ ] Estender `JpaDocumentRepository.kt`:
      `findAllByUserIdOrderByCreatedAtDesc(userId, Pageable)` e
      `deleteByIdAndUserId(id, userId)` (derived delete retornando contagem)
- [ ] Implementar os métodos novos em `DocumentRepositoryJpaAdapter.kt`
      (conversão `Page<DocumentEntity>` → `PageResult<Document>`;
      `@Transactional` no delete)
- [ ] Teste de integração (Testcontainers) do adapter: busca por id do dono vs
      de outro usuário, delete remove documento **e chunks** (verificar tabela
      `chunks` vazia via cascade), paginação com ordenação `created_at DESC`
- [ ] Registrar `GetDocumentUseCase` e `DeleteDocumentUseCase` como `@Bean` em
      `UseCaseConfig.kt`

## API

- [ ] Criar `api/dto/PagedResponse.kt` (mapeado de `PageResult`)
- [ ] Adicionar `GET /documents/{id}` no `DocumentController.kt` → `200` com
      `DocumentResponse`
- [ ] Adicionar `DELETE /documents/{id}` no `DocumentController.kt` → `204 No
      Content`
- [ ] Alterar `GET /documents` para `page`/`size` com defaults (`0`/`20`) e
      Bean Validation (`@Min(0)`, `@Min(1)`/`@Max(100)`) → `PagedResponse`
- [ ] Mapear `DocumentNotFoundException` → `404 ProblemDetail` no
      `DocumentExceptionHandler.kt`

## Testes de integração (Kotest + Testcontainers)

- [ ] `GET /documents/{id}` com sucesso → `200` + metadados corretos
- [ ] `GET /documents/{id}` de documento de outro usuário → `404` (mesma
      resposta de inexistente)
- [ ] `GET /documents/{id}` de documento inexistente → `404`
- [ ] `DELETE /documents/{id}` com sucesso → `204`; documento some do
      `GET /documents` e chunks somem do banco
- [ ] `DELETE /documents/{id}` de documento inexistente ou de outro usuário →
      `404`
- [ ] Após o delete, `POST /questions` não usa mais os chunks removidos como
      fonte (critério 3 do requirements)
- [ ] `GET /documents` paginado com mais de uma página: tamanhos, `totalItems`,
      `totalPages` e ordenação corretos
- [ ] `GET /documents` com parâmetro inválido (`page=-1`, `size=0`,
      `size=101`) → `400` com detalhe do campo
- [ ] Os três endpoints sem token → `401`

## Fechamento do marco

- [ ] Rodar `./gradlew build` limpo (build + todos os testes)
- [ ] Atualizar README (novos endpoints com exemplos de `curl`, formato da
      resposta paginada) e marcar progresso em `00-architecture.md`/`01-roadmap.md`
- [ ] Validar fluxo completo via `docker compose up`: buscar por id, deletar e
      confirmar sumiço dos chunks, listar paginado, parâmetros inválidos
- [ ] Commit(s) semânticos ao longo da implementação
- [ ] Abrir PR de `feature/m5-gestao-documentos` para `develop`

## Definição de pronto (Definition of Done)

Todos os critérios de aceite do `M5-requirements.md` atendidos, testes de
integração cobrindo os fluxos listados acima, CI verde no PR.

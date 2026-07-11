# M5 — Gestão de documentos — Design

## Decisões técnicas

| Item | Escolha | Justificativa |
|---|---|---|
| Estratégia de paginação | Offset-based (`LIMIT/OFFSET`), com `Pageable` do Spring Data **só no adapter** | Volume esperado por usuário é pequeno (dezenas/centenas de documentos) — a fraqueza do offset (custo cresce com a profundidade da página, itens podem duplicar/pular entre páginas sob escrita concorrente) não se manifesta nessa escala. Keyset/cursor resolveria isso, mas complica o cliente (sem salto de página, sem "página X de Y") — registrado como evolução no M9 se o volume justificar |
| Parâmetros de paginação | `page` (0-based, default `0`) e `size` (default `20`, máximo `100`) | Convenção do próprio Spring Data (0-based) — não inventar uma diferente. Máximo de 100 impede um cliente de pedir `size=100000` e transformar a paginação em full scan |
| Validação dos parâmetros | Bean Validation nos `@RequestParam` (`@Min(0)` em page, `@Min(1)`/`@Max(100)` em size) | Mesmo mecanismo já usado nos DTOs do M1; o Spring converte a violação em `400 Bad Request` com `ProblemDetail` de graça (`spring.mvc.problemdetails.enabled=true` já ativo) |
| Formato da resposta paginada | DTO próprio `PagedResponse<T>` (`items`, `page`, `size`, `totalItems`, `totalPages`), sobre um `PageResult<T>` do domínio | Expor `Page<T>` do Spring serializaria campos internos (`pageable`, `sort`...) e acoplaria o contrato público da API ao Spring Data. Com `PageResult` no `domain`, `application` continua sem dependência de framework (regra do projeto) e a conversão `Pageable`/`Page` fica isolada no adapter JPA |
| Remoção dos chunks | Confiar no `ON DELETE CASCADE` de `chunks.document_id`, que **já existe** desde a `V3` | Atômico (mesma transação do delete do documento), zero código para manter, impossível "esquecer" a limpeza em um novo caminho de deleção. O teste de integração verifica os chunks sumindo, documentando o comportamento. Sem migration nova neste marco |
| Status do `DELETE` | `204 No Content` | Convenção REST para remoção sem corpo; o cliente já tinha os metadados antes de deletar |
| 404 para documento de outro usuário | `findByIdAndUserId`/`deleteByIdAndUserId` — o `userId` do token entra na própria query | Uma query só resolve busca + posse; impossível vazar documento alheio por esquecimento de um `if`. Documento de outro usuário e documento inexistente são indistinguíveis na resposta (mesmo princípio anti-enumeração do M1) |
| Exceção de domínio | `DocumentNotFoundException` → `404` via `DocumentExceptionHandler` | Mesmo padrão das exceções existentes (`EmptyDocumentException` etc.): o domínio expressa a regra, o handler da API traduz para HTTP |
| Casos de uso | `GetDocumentUseCase` e `DeleteDocumentUseCase` novos; `ListDocumentsUseCase` passa a receber `page`/`size` | Segue o padrão do projeto de um caso de uso por operação (`IngestDocumentUseCase`, `AskQuestionUseCase`...), cada um testável com fakes |
| Ordenação da listagem | Fixa em `created_at DESC` (sem parâmetro `sort`) | Já é a ordenação atual do `findAllByUserIdOrderByCreatedAtDesc`; parâmetro de ordenação configurável seria especulação sem caso de uso |

## Onde entra cada peça (Clean Architecture)

```
domain/
├── model/
│   └── PageResult.kt                  # genérico: items, page, size, totalItems (+ totalPages derivado) — sem Spring
├── exception/
│   └── DocumentNotFoundException.kt   # documento não existe OU não pertence ao usuário
└── port/
    └── DocumentRepository.kt          # +findByIdAndUserId, +deleteByIdAndUserId, findAllByUserId ganha page/size

application/
├── GetDocumentUseCase.kt              # busca por id no escopo do usuário; null → DocumentNotFoundException
├── DeleteDocumentUseCase.kt           # deleta no escopo do usuário; nada deletado → DocumentNotFoundException
└── ListDocumentsUseCase.kt            # (alterado) recebe page/size, retorna PageResult<Document>

infrastructure/
├── persistence/
│   ├── JpaDocumentRepository.kt       # +findAllByUserIdOrderByCreatedAtDesc(Pageable), +deleteByIdAndUserId
│   └── DocumentRepositoryJpaAdapter.kt # implementa os métodos novos; converte Page<Entity> → PageResult<Document>
└── UseCaseConfig.kt                   # @Bean para GetDocumentUseCase e DeleteDocumentUseCase

api/
├── DocumentController.kt              # +GET /documents/{id}, +DELETE /documents/{id}; list ganha page/size validados
├── dto/
│   └── PagedResponse.kt               # items, page, size, totalItems, totalPages — mapeado de PageResult
└── DocumentExceptionHandler.kt        # +DocumentNotFoundException → 404 ProblemDetail
```

**Por que `PageResult` fica em `domain/model`**: a porta `DocumentRepository`
(que é do domínio) precisa retornar "uma página de documentos" — se o tipo de
retorno fosse `Page<T>` do Spring Data, o domínio dependeria de framework,
violando a regra de dependência do projeto. `PageResult` é uma data class pura;
o adapter JPA traduz `Page<DocumentEntity>` para ela.

## Fluxo — GET /documents/{id}

1. `DocumentController.get` recebe o `id` do path e o `userId` do
   `@AuthenticationPrincipal`
2. `GetDocumentUseCase.get(userId, id)` chama
   `DocumentRepository.findByIdAndUserId(id, userId)`
3. Retorno `null` (não existe ou é de outro usuário) → lança
   `DocumentNotFoundException` → handler responde `404` (mesma resposta nos
   dois casos)
4. Encontrado → controller retorna `200` com `DocumentResponse` (DTO já
   existente do M2)

## Fluxo — DELETE /documents/{id}

1. `DocumentController.delete` recebe `id` + `userId`
2. `DeleteDocumentUseCase.delete(userId, id)` chama
   `DocumentRepository.deleteByIdAndUserId(id, userId)`, que informa se algo
   foi deletado (contagem/boolean)
3. Nada deletado → `DocumentNotFoundException` → `404`
4. Deletado → o `ON DELETE CASCADE` da FK (migration `V3`) remove os chunks na
   mesma transação → controller responde `204 No Content`

## Fluxo — GET /documents paginado

1. `DocumentController.list` recebe `page`/`size` (`@RequestParam` com defaults
   e Bean Validation; violação → `400` automático)
2. `ListDocumentsUseCase.list(userId, page, size)` chama
   `DocumentRepository.findAllByUserId(userId, page, size)`
3. Adapter monta `PageRequest.of(page, size)`, consulta
   `findAllByUserIdOrderByCreatedAtDesc(userId, pageable)` e converte o
   `Page` em `PageResult<Document>` (o `Page` já traz `totalElements` via
   count query)
4. Controller mapeia para `PagedResponse<DocumentResponse>`:

```json
{
  "items": [ { "id": "…", "filename": "relatorio.pdf", "chunkCount": 12, "createdAt": "…" } ],
  "page": 0,
  "size": 20,
  "totalItems": 37,
  "totalPages": 2
}
```

## Migration Flyway

Nenhuma — a cascata (`ON DELETE CASCADE`) e os índices necessários
(`idx_documents_user_id`, `idx_chunks_document_id`) já existem desde a `V3`.

## Dependências novas (build.gradle.kts)

Nenhuma.

## Configuração (application.yaml)

Nenhuma mudança.

## Trade-offs conscientes

- **Breaking change no `GET /documents`**: a resposta deixa de ser um array puro
  e vira o objeto paginado. Aceitável porque a API ainda não tem consumidor
  externo (deploy público só no M7) — fazer isso agora é exatamente o motivo do
  M5 vir antes.
- **Count query a cada página**: `totalItems` exige um `SELECT count(*)` extra
  por requisição. Irrelevante nessa escala e é o que permite `totalPages` na
  resposta; se doesse, a alternativa seria uma resposta sem total (`Slice` do
  Spring / keyset).
- **Cascata invisível no código Kotlin**: quem lê só o adapter não vê os chunks
  sendo removidos — a regra mora no schema (`V3`). Mitigado pelo teste de
  integração que verifica a tabela `chunks` após o delete e por esta doc.
- **Deleção definitiva**: sem soft delete/lixeira; recuperar exige re-ingestão
  (o binário original não é armazenado). Consciente e documentado no
  requirements.

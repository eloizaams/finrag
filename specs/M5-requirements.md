# M5 — Gestão de documentos — Requirements

## Objetivo do marco

Fechar as lacunas do CRUD de documentos deixadas pelo M2: hoje a API só permite
ingerir (`POST /documents`) e listar tudo de uma vez (`GET /documents`). Este
marco adiciona consulta individual, remoção (com limpeza dos chunks e seus
embeddings, que custam armazenamento e poluem a busca semântica do M3) e
paginação da listagem — completando a superfície de API de documentos prevista
em `00-architecture.md` antes de expô-la publicamente no M7.

## Critérios de aceite

1. `GET /documents/{id}` retorna `200 OK` com os metadados do documento
   (mesmos campos do `DocumentResponse` atual) quando o documento existe e
   pertence ao usuário autenticado.
2. `GET /documents/{id}` de documento inexistente **ou pertencente a outro
   usuário** retorna `404 Not Found` nos dois casos, com a mesma resposta —
   não revelar que o documento existe (mesmo princípio do critério 5 do M1).
3. `DELETE /documents/{id}` remove o documento **e todos os seus chunks**
   (incluindo embeddings) quando ele pertence ao usuário autenticado; após a
   remoção, o documento some do `GET /documents` e seus chunks não aparecem
   mais como fonte em respostas do `POST /questions`.
4. `DELETE /documents/{id}` de documento inexistente ou de outro usuário
   retorna `404 Not Found` (mesma regra do critério 2).
5. `GET /documents` aceita parâmetros de paginação e retorna, além da página
   de documentos, metadados de paginação (total de itens, página atual —
   formato exato definido no design.md). Sem parâmetros, usa valores padrão.
6. Parâmetro de paginação inválido (página negativa, tamanho acima do máximo
   etc.) retorna `400 Bad Request` com detalhe do campo, não `500`.
7. Os três endpoints exigem JWT válido (`401` sem token) e operam sempre no
   escopo do usuário autenticado — nunca listam/afetam documentos de terceiros.
8. Testes de integração (Kotest + Testcontainers) cobrem pelo menos: busca por
   id com sucesso, busca de documento de outro usuário (404), delete com
   sucesso verificando que os chunks sumiram do banco, delete de documento
   inexistente (404), listagem paginada com mais de uma página e parâmetro de
   paginação inválido (400).

## Fora de escopo neste marco

- Atualizar/renomear documento (`PUT`/`PATCH`) — não há caso de uso; re-ingerir é o caminho
- Download do arquivo original — o binário não é armazenado (decisão do M2)
- Re-ingestão/reprocessamento de um documento existente
- Busca/filtro por nome de arquivo na listagem
- Soft delete / lixeira — remoção é definitiva
- Deleção em lote (`DELETE /documents`)
- Multi-tenancy real e ingestão assíncrona — backlog M9 (`CLAUDE.md`)

## Perguntas em aberto (decidir no design.md)

- Paginação: offset-based com `Pageable` do Spring Data ou keyset/cursor? Quais
  nomes de parâmetro (`page`/`size`?), valores padrão e tamanho máximo de página?
- Formato da resposta paginada: expor o `Page<T>` do Spring direto ou DTO
  próprio (`items` + `page` + `totalItems`...)? Como isso atravessa a Clean
  Architecture sem `application`/`domain` dependerem de Spring Data?
- Cascata de chunks: confiar no `ON DELETE CASCADE` que **já existe** na
  migration V3 ou deletar explicitamente via repositório? (Trade-off:
  simplicidade vs. visibilidade/testabilidade da regra no código)
- `DELETE` retorna `204 No Content` ou `200` com corpo?
- Novos casos de uso (`GetDocumentUseCase`, `DeleteDocumentUseCase`) ou métodos
  nos existentes? Quais métodos novos a porta `DocumentRepository` ganha?

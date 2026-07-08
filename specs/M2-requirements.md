# M2 — Ingestão de documentos — Requirements

## Objetivo do marco

Permitir que um usuário autenticado envie documentos (PDF ou Markdown) via
`POST /documents` e que a API execute o pipeline de ingestão completo descrito
em `specs/00-architecture.md`: extração de texto → chunking → geração de
embeddings (OpenAI `text-embedding-3-small`) → persistência no
PostgreSQL/pgvector. Sem este marco não há nada indexado para o M3 (Q&A RAG)
buscar — é a metade "escrita" do pipeline RAG.

## Critérios de aceite

1. `POST /documents` aceita upload multipart de um arquivo PDF ou Markdown e
   retorna `201 Created` com os metadados do documento: `id`, `filename`,
   quantidade de chunks gerados e data de criação.
2. O documento fica associado ao usuário autenticado (claim `sub` do JWT) —
   base para o M3 responder perguntas só sobre os documentos de quem pergunta.
3. Ao final da ingestão, cada chunk está persistido com seu texto e seu
   embedding (vetor de 1536 dimensões) no pgvector, e a quantidade de chunks
   retornada na resposta bate com o que está no banco.
4. Arquivo com formato não suportado (ex.: `.docx`, `.png`) retorna
   `415 Unsupported Media Type` com `ProblemDetail`, não `500`.
5. Arquivo vazio ou do qual não se consegue extrair texto algum retorna
   `422 Unprocessable Entity` — nada é persistido nesse caso.
6. Arquivo acima do tamanho máximo (definir em design.md) retorna
   `413 Payload Too Large`.
7. Falha na chamada de embeddings (OpenAI fora do ar, chave inválida) retorna
   `502 Bad Gateway` com `ProblemDetail` e **não deixa documento pela metade**
   no banco (ingestão é atômica: ou tudo, ou nada).
8. `GET /documents` retorna `200` com a lista de documentos **do usuário
   autenticado** (metadados apenas, sem chunks nem embeddings).
9. Ambos endpoints exigem JWT válido — sem token retorna `401` (herdado do
   M1, sem reconfiguração).
10. Chave da API OpenAI vem de variável de ambiente, nunca hardcoded nem
    commitada, e nunca aparece em logs.
11. Testes de integração (Kotest + Testcontainers, Postgres real com pgvector)
    cobrem pelo menos: ingestão de PDF com sucesso, ingestão de Markdown com
    sucesso, formato não suportado, arquivo vazio, falha do provedor de
    embeddings sem persistência parcial, listagem retornando só os documentos
    do usuário do token, e acesso sem token. Chamadas à OpenAI **não**
    acontecem de verdade nos testes (estratégia a definir no design.md).

## Fora de escopo neste marco

- Busca semântica e Q&A — é o M3 consumir o que este marco indexa
- Ingestão assíncrona via fila — ADR-05 decidiu síncrono no MVP (candidato a M9)
- Formatos além de PDF/Markdown (fora de escopo da v1 no `CLAUDE.md`)
- OCR de PDFs escaneados (só PDFs com camada de texto)
- `DELETE /documents/{id}` / atualização ou re-ingestão de documento
- Deduplicação de conteúdo (subir o mesmo arquivo duas vezes cria dois documentos)
- Multi-tenancy real (isolamento é por `userId`, conforme v1)

## Perguntas em aberto (decidir no design.md)

- Biblioteca de extração de PDF: PDFBox (como sugere a arquitetura) — qual
  versão/estratégia de extração? E Markdown: tratar como texto puro ou parsear
  a estrutura (ex.: commonmark) para remover marcação?
- Estratégia de chunking: quebrar por parágrafo com overlap — mas qual o
  tamanho-alvo do chunk (caracteres? tokens?) e qual o tamanho do overlap?
- Cliente HTTP para a API da OpenAI: `RestClient` do Spring na mão ou SDK
  oficial? Chamada de embeddings em lote (batch) ou um chunk por vez?
- Como mapear a coluna `vector(1536)` no JPA/Hibernate: suporte nativo do
  Hibernate a vetores, query nativa, ou JDBC direto?
- Índice vetorial já neste marco (HNSW/IVFFlat) ou só no M3, quando a busca
  existir de fato?
- Qual o limite de tamanho de arquivo?
- Como garantir a atomicidade (critério 7): transação única envolvendo a
  chamada externa, ou chamar embeddings antes de abrir a transação?
- Estratégia de teste sem chamar a OpenAI real: fake do port
  `EmbeddingProvider` no contexto Spring de teste ou WireMock na camada HTTP?

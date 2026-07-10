# M3 — Q&A RAG — Requirements

## Objetivo do marco

Permitir que um usuário autenticado envie uma pergunta em linguagem natural via
`POST /questions` e que a API execute o pipeline de consulta completo descrito
em `specs/00-architecture.md`: embedding da pergunta (OpenAI
`text-embedding-3-small`) → busca por similaridade de cosseno no
PostgreSQL/pgvector, restrita aos documentos do usuário → montagem de contexto
com os top-k chunks mais relevantes → geração de resposta via LLM (Claude
Haiku, API Anthropic) → resposta fundamentada, com as fontes usadas. Este
marco consome o que o M2 indexou — é a metade "leitura" do pipeline RAG.

## Critérios de aceite

1. `POST /questions` aceita `{ "question": "<texto>" }` e retorna `200 OK`
   com a resposta gerada e a lista de fontes usadas: para cada fonte,
   `documentId`, `filename`, o trecho do chunk e o score de similaridade.
2. A busca considera **apenas** chunks de documentos do usuário autenticado
   (claim `sub` do JWT) — mesmo isolamento por `userId` do `GET /documents`
   do M2; um usuário nunca recebe contexto vindo de documentos de outro
   usuário.
3. A resposta é fundamentada nos chunks recuperados: o prompt enviado ao LLM
   instrui explicitamente a responder somente com base no contexto fornecido
   e a declarar quando o contexto não contém informação suficiente para
   responder, em vez de inventar uma resposta.
4. Usuário sem nenhum documento indexado, ou cuja busca não retorna nenhum
   chunk, recebe `200 OK` com uma resposta padrão informando que não há
   conteúdo indexado para responder à pergunta, e lista de fontes vazia —
   isso **não** é um erro do cliente.
5. Pergunta em branco, vazia ou ausente no corpo da requisição retorna
   `400 Bad Request` com `ProblemDetail`.
6. Falha na chamada de embeddings da pergunta (OpenAI fora do ar, chave
   inválida) ou falha na chamada ao LLM (Anthropic fora do ar, chave
   inválida, timeout) retorna `502 Bad Gateway` com `ProblemDetail` —
   mesmo padrão de erro externo já estabelecido no M2 para a OpenAI.
7. O endpoint exige JWT válido — sem token retorna `401` (herdado do M1, sem
   reconfiguração de segurança).
8. Chave da API Anthropic vem de variável de ambiente
   (`ANTHROPIC_API_KEY`), nunca hardcoded nem commitada, e nunca aparece em
   logs — mesma regra já aplicada a `OPENAI_API_KEY` (M2) e `JWT_SECRET`
   (M1).
9. Testes de integração (Kotest + Testcontainers, Postgres real com
   pgvector) cobrem pelo menos: pergunta com resposta encontrada (busca
   vetorial real contra embeddings determinísticos fake, chunk mais
   relevante retornado em primeiro lugar), isolamento por usuário (chunks
   de outro usuário nunca entram no contexto nem nas fontes), usuário sem
   documentos indexados, pergunta em branco, falha do provedor de
   embeddings, falha do provedor de LLM, e acesso sem token. Nem a OpenAI
   nem a Anthropic são chamadas de verdade nos testes (estratégia a definir
   no design.md, reaproveitando o padrão de fakes do M2).

## Fora de escopo neste marco

- Filtrar a busca por um `documentId` específico informado na pergunta —
  a busca sempre considera todos os documentos do usuário
- Histórico de perguntas persistido (`GET /questions` ou similar) —
  cada pergunta é respondida de forma independente, sem estado
- Conversação multi-turno / contexto de perguntas anteriores
- Re-ranking dos resultados de busca (fora de escopo da v1 no `CLAUDE.md`)
- Streaming de resposta via SSE (fora de escopo da v1 no `CLAUDE.md`,
  candidato a M9)
- Avaliação automatizada de qualidade de RAG com golden dataset (candidato
  a M9, mencionado no `00-architecture.md`)
- Multi-tenancy real (isolamento continua sendo por `userId`, conforme v1)
- Ajuste fino de `top-k` ou threshold de similaridade baseado em métricas —
  valores iniciais são heurística, não resultado de avaliação

## Perguntas em aberto (decidir no design.md)

- Como expressar a busca por similaridade de cosseno no pgvector a partir do
  JPA/Hibernate: o `hibernate-vector` (M2) mapeia a coluna `vector(1536)`,
  mas não gera `ORDER BY` por distância — query nativa, JDBC direto, ou
  outra estratégia?
- HNSW ou IVFFlat para o índice vetorial em `chunks.embedding` — decisão que
  o M2-design explicitamente adiou para este marco, agora que existe uma
  query real para justificá-la?
- Qual o valor de `top-k` (quantidade de chunks recuperados)? Faz sentido
  aplicar um threshold mínimo de similaridade, ou deixar o prompt lidar com
  contexto pouco relevante?
- Cliente HTTP para a API da Anthropic: `RestClient` do Spring na mão
  (mesma decisão do cliente OpenAI no M2), qual modelo Claude usar, e qual
  o formato exato da requisição/resposta de `POST /v1/messages`?
- Como estruturar o prompt de RAG (system prompt + injeção dos chunks) de
  forma que fique fácil de explicar e defender em entrevista, e que reduza
  alucinação quando o contexto é insuficiente?
- Como simular a busca vetorial nos testes de integração sem depender de
  embeddings reais da OpenAI — vetores determinísticos construídos à mão
  que garantam uma ordem de similaridade previsível?
- Estratégia de teste para o cliente Anthropic sem chamá-lo de verdade —
  `MockRestServiceServer`, como no M2?

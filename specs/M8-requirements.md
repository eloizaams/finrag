# M8 — Avaliação de RAG — Requirements

## Objetivo do marco

Medir com dados a qualidade do retrieval do pipeline RAG — hoje `topK = 5` e
`minSimilarity = 0.25` foram escolhidos "no olho" (`RagProperties`), sem
evidência de que retornam os chunks certos. O marco cria um golden dataset
(perguntas com fonte esperada conhecida), um harness que mede a busca semântica
contra ele, calibra os parâmetros com base nos números e publica metodologia e
resultados no README. Fecha a pergunta de entrevista mais difícil sobre o
projeto: "como você sabe que seu RAG funciona?".

## Critérios de aceite

1. Golden dataset versionado no repositório com 15–25 casos, cada um contendo:
   pergunta, documento-fonte esperado, trecho esperado e resposta de
   referência. Inclui pelo menos um caso cuja resposta exige mais de um chunk
   e pelo menos dois casos **sem resposta nos documentos**.
2. Harness de avaliação executável por comando dedicado: sobe Postgres+pgvector
   efêmero (Testcontainers), ingere os documentos de fixture pelo pipeline real
   de ingestão e mede o retrieval com embeddings reais da OpenAI.
3. Métricas reportadas por caso e agregadas: recall@k (o chunk esperado aparece
   no top-k?) e MRR (em que posição aparece?).
4. Relatório legível ao final da execução: resumo no console + arquivo
   versionável com as métricas e a lista de casos que falharam.
5. `./gradlew build` continua verde e **sem chamar nenhuma API externa** — o
   harness fica fora do ciclo padrão de build/CI.
6. Para os casos "sem resposta", o harness verifica que nenhum chunk supera o
   threshold (comportamento de recusa preservado).
7. Calibração feita: harness executado com um grid pequeno de `topK` ×
   `minSimilarity`, e os valores finais escolhidos com justificativa registrada
   (mantidos ou alterados).
8. `RagProperties`/configuração de produção atualizados se a calibração indicar
   valores melhores que os atuais.
9. README com seção "Avaliação de RAG": metodologia, números da última
   execução e como rodar o harness.
10. ADR-07 (estratégia de avaliação de RAG) registrado em `00-architecture.md`.
11. Custo por execução do harness estimado e documentado (apenas embeddings de
    perguntas — deve ficar em centavos).

## Fora de escopo neste marco

- Avaliação da resposta gerada pelo LLM (LLM-as-judge / groundedness) —
  candidato a extensão do próprio M8 ou ao backlog M9, decidir ao final
- Re-ranking de busca — segue no M9; o M8 cria justamente a régua para medir
  seu ganho no futuro
- Avaliação rodando no CI de todo PR — execução manual/sob demanda basta
- Frameworks prontos de avaliação (Ragas, promptfoo, etc.) — coerente com o
  ADR-04 (pipeline manual, sem frameworks de RAG)
- Dataset grande / estatisticamente robusto — o conjunto é pequeno e curado;
  o valor está no método, não no tamanho

## Perguntas em aberto (decidir no design.md)

- Formato do dataset: JSON ou YAML? E como identificar o "chunk esperado" —
  substring do texto, id do documento, ou os dois?
- Onde vive o harness: `src/test` com tag Kotest excluída do build, ou source
  set Gradle separado (`eval`)?
- Nome e mecânica do comando: `./gradlew ragEval` filtrando a tag?
- Documentos de fixture: reutilizar os existentes dos testes ou criar 2–3
  documentos financeiros novos, mais ricos, só para avaliação?
- O harness mede chamando `ChunkSearchRepository` direto (só retrieval) ou
  atravessa o `AskQuestionUseCase` com LLM fake (fluxo completo sem custo de
  geração)?
- Grid de calibração: quais valores de `topK` e `minSimilarity` testar?
- Onde salvar o relatório: `build/reports/rag-eval/` (descartável) ou arquivo
  versionado em `docs/`?

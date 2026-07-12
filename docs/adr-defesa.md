# Defesa das decisões de arquitetura (ADR por ADR)

Preparação para entrevista: a pergunta provável sobre cada ADR, uma resposta
modelo em primeira pessoa e o trade-off que eu admito de saída — porque
reconhecer o custo da decisão é o que a torna crível. Os ADRs resumidos estão
em [`specs/00-architecture.md`](../specs/00-architecture.md); os números
citados vêm da avaliação do M8 ([`rag-eval.md`](rag-eval.md)).

## ADR-01 — pgvector como vector store

**Pergunta provável:** "Por que não um vector DB dedicado, tipo Pinecone ou
Qdrant?"

**Resposta:** Porque eu já tinha PostgreSQL na arquitetura e o pgvector me dá
busca vetorial dentro dele — sem um segundo sistema para operar, pagar e manter
consistente. O ganho mais concreto é transacional: documento, chunks e
embeddings são persistidos numa transação só; se a ingestão falha no meio, não
sobra vetor órfão apontando para um documento que não existe. Com um vector DB
externo eu precisaria de saga ou reconciliação para garantir isso. O pgvector é
performático até a casa de ~1M de vetores, e meu corpus está ordens de
grandeza abaixo disso.

**Trade-off admitido:** em escala de dezenas de milhões de vetores com alta
concorrência, um vector DB dedicado (com HNSW distribuído, sharding) passa a
fazer sentido — mas essa escala é hipotética aqui, e otimizar para ela agora
seria complexidade especulativa.

## ADR-02 — OpenAI `text-embedding-3-small` para embeddings

**Pergunta provável:** "Por que embeddings da OpenAI se a resposta é gerada
pela Anthropic? Não seria mais simples um provedor só?"

**Resposta:** Seria, mas a Anthropic não oferece API de embeddings — então um
segundo provedor era inevitável (ou um modelo local). O `text-embedding-3-small`
tem custo desprezível no meu volume: a avaliação inteira do M8, com 25
perguntas embedadas, custa ~US$ 0,0001. Um modelo local (ONNX) eliminaria a
dependência, mas complicaria o deploy no free tier de 512MB sem ganho real para
o objetivo do projeto. O importante arquiteturalmente: o provedor está atrás da
porta `EmbeddingProvider` no domínio, então trocar OpenAI por Cohere ou por um
modelo local é uma implementação nova de interface, sem tocar no core.

**Trade-off admitido:** dois provedores externos = dois pontos de falha e duas
chaves para gerenciar. Mitigado com métrica própria de erros por provedor
(`finrag.provider.errors`) e mapeamento para `502` com `ProblemDetail`.

## ADR-03 — Claude Haiku como LLM de resposta

**Pergunta provável:** "Por que o Haiku e não um modelo maior? A resposta não
ficaria melhor?"

**Resposta:** Para Q&A fundamentado em contexto curto, o gargalo não é o
tamanho do modelo — é a qualidade do contexto que chega ao prompt. Foi
exatamente isso que o M8 confirmou: com recall@5 de 95%, o chunk certo quase
sempre está no prompt, e o trabalho do LLM é extração e síntese, não raciocínio
longo. O Haiku é rápido e barato o suficiente para uma API pública de portfólio
com rate limiting. E, como no ADR-02, ele está atrás da porta `LlmClient`:
subir para um modelo maior é troca de configuração, não refatoração.

**Trade-off admitido:** perguntas que exigem sintetizar muitos chunks ou fazer
contas sobre eles podem sair melhores num modelo maior. Se a avaliação da
resposta gerada (LLM-as-judge, backlog M9) mostrar isso, a troca é barata.

## ADR-04 — Pipeline RAG à mão (sem Spring AI/LangChain4j)

**Pergunta provável:** "Por que reinventar a roda em vez de usar Spring AI?"

**Resposta:** Porque o objetivo do projeto é demonstrar que eu entendo cada
etapa do RAG, não que eu sei chamar uma abstração pronta. Extração, chunking
com overlap, embeddings, busca por cosseno no pgvector e montagem do prompt são
código meu — e por isso eu consigo explicar qualquer linha. O M8 é a prova de
que isso não ficou amador: dá para calibrar (`topK=5` e `minSimilarity=0.25`
saíram de um grid de calibração, não de default de framework) e medir
(recall@5 = 95%, MRR = 0,75) o que foi escrito à mão. Num time e num produto
reais, com prazo, eu provavelmente partiria do Spring AI — aqui a decisão
otimiza aprendizado e defensabilidade.

**Trade-off admitido:** mais código para manter e sem as integrações prontas do
ecossistema (re-rankers, outros stores). O escopo pequeno e estável do projeto
mantém esse custo baixo.

## ADR-05 — Ingestão síncrona no MVP

**Pergunta provável:** "E se alguém subir um PDF gigante? A requisição não
trava?"

**Resposta:** O caso está delimitado por design: upload de até 10MB, com a
requisição de ingestão respondendo só depois de extrair, chunkar, embedar e
persistir — atômico e simples de raciocinar. Abuso é contido por rate limiting
por usuário (5 uploads/min) e o tamanho, pelo limite do multipart (`413` acima
disso). Fila assíncrona (com status de processamento, retry, idempotência)
resolveria arquivos maiores, mas triplicaria a complexidade do MVP sem mudar a
demonstração de competência. Está registrada como evolução consciente no
backlog M9 — eu sei o caminho, escolhi não trilhá-lo ainda.

**Trade-off admitido:** um PDF de 10MB denso pode dar uma requisição de vários
segundos; a UX de "processando..." com polling não existe. Para o perfil de uso
(recrutador testando com documentos pequenos), é aceitável.

## ADR-06 — Clean Architecture em 4 camadas

**Pergunta provável:** "Clean Architecture não é overkill para um projeto desse
tamanho?"

**Resposta:** O tamanho do projeto não é o critério — o número de dependências
externas voláteis é. Aqui são três (OpenAI, Anthropic, pgvector), e a regra de
dependência garante que nenhuma delas vaza para o domínio: `EmbeddingProvider`,
`LlmClient` e os repositórios são portas; OpenAI e Anthropic são detalhes de
`infrastructure`. Isso já pagou dividendo prático: os casos de uso têm teste
unitário puro, sem Testcontainers, e o harness do M8 reusa o pipeline real de
ingestão trocando só a borda. É a versão pragmática — quatro pacotes e uma
regra de import, não uma cerimônia de interfaces para tudo.

**Trade-off admitido:** mais arquivos e algum mapeamento entre camadas do que
um controller-service-repository direto. Em troca, trocar provedor é criar uma
classe, não caçar imports.

## ADR-07 — Avaliação de RAG: golden dataset próprio, só de retrieval

**Pergunta provável:** "Como você sabe que o seu RAG funciona? E por que não
avaliar a resposta final, que é o que o usuário vê?"

**Resposta:** Avaliei o retrieval porque ele é o teto da qualidade: a resposta
nunca é melhor que o contexto que chega ao prompt — e retrieval é
determinístico e barato de medir (~US$ 0,0001/rodada), então dá para rodar a
cada mudança. Montei um golden dataset de 25 casos (diretos, multi-chunk,
ambíguos e sem resposta, sempre parafraseados para não inflar a similaridade) e
um harness que computa o grid `topK × minSimilarity` inteiro com uma única
rodada de embeddings. Resultado: recall@5 = 95%, MRR = 0,75, e `k=5` validado
pelos dados (k=3 perde 9 p.p., k=8 não ganha nada). O achado mais valioso foi
negativo: nenhum threshold separa "tem resposta" de "não tem" — acertos em
0,46–0,76 contra irrelevantes em 0,55–0,71 —, então a recusa pertence ao prompt
do LLM, com evidência. E conheço a falha: a pergunta com "bitcoin" não acha o
chunk de "criptoativos" (rank 9) — gap de vocabulário, motivação medida para
re-ranking no M9.

**Trade-off admitido:** 25 casos não têm valor estatístico e quem escreveu o
corpus escreveu as perguntas — o entregável é o método e a régua de regressão,
não um benchmark. A qualidade da resposta gerada segue sem métrica automática
(LLM-as-judge no backlog, por custo e não-determinismo).

## Perguntas transversais

**"O que você faria diferente se começasse hoje?"** — Escreveria o golden
dataset junto com o M3, não no M8: eu calibrei `topK`/`minSimilarity` no olho
primeiro e só depois confirmei com dados. O resultado validou os valores, mas
foi sorte parcial — avaliação deveria nascer com o pipeline.

**"Como isso escala?"** — Os gargalos conhecidos, na ordem: ingestão síncrona
(→ fila, M9), rate limiting em memória de instância única (→ Redis se houver
réplicas), pgvector com scan exato (→ índice HNSW/IVFFlat bem antes de 1M de
vetores). Nenhum exige redesenho: as portas do domínio e o schema via Flyway
foram pensados para essas trocas.

**"Qual é o próximo passo técnico?"** — Re-ranking, porque é a única evolução
com problema **medido** esperando por ela: o caso `vedacao-cripto` documenta o
gap de vocabulário, e o harness do M8 é a régua que diria exatamente quantos
pontos de recall o re-ranker compra.

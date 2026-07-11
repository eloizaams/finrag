# 01 — Roadmap

## Como ler este documento

Visão macro do que vem depois do M3. Cada linha é um marco candidato: tema,
motivação e valor esperado. Quando um marco começa de verdade, ele ganha o
trio `MX-requirements.md` / `MX-design.md` / `MX-tasks.md` (via skill
`new-milestone`) — as decisões técnicas ficam lá, não aqui. Este arquivo não
fixa datas nem compromissos, só ordem de prioridade e o porquê dela.

## Marcos planejados

| Marco | Tema | Motivação | Status |
|-------|------|-----------|--------|
| M4 | Observabilidade do pipeline RAG | Logs estruturados JSON e métricas (Micrometer/Actuator) já aparecem no diagrama de `00-architecture.md`, mas nenhum marco entregou isso. Sem instrumentação, não dá para responder "quanto custa uma pergunta" ou "onde está a latência" — pergunta óbvia de entrevista sobre sistemas com LLM | ✅ Concluído |
| M5 | Gestão de documentos | Fecha lacunas do CRUD: `GET /documents/{id}`, `DELETE /documents/{id}` (com remoção em cascata dos chunks) e paginação em `GET /documents`. Marco pequeno, mas fecha uma superfície de API incompleta | ✅ Concluído |
| M6 | Docs da API + hardening | OpenAPI/Swagger UI para quem for testar o portfólio sem ler o código, rate limiting nos endpoints que chamam APIs pagas (embeddings/LLM), refino de validações de entrada | 🔜 Planejado |
| M7 | Deploy | Imagem Docker da aplicação e deploy em free tier (Render/Fly.io) com CI/CD, tornando o portfólio acessível por URL pública. Depende de M6 (rate limiting) para não expor custo de API sem proteção | 🔜 Planejado |
| M8 | Reservado / a definir | Sem tema concreto ainda. Reavaliar depois do M7: pode nascer de uma necessidade que só aparece rodando M4-M7 em produção (ex.: performance sob carga), ou continuar vago até então | ⬜ Não alocado |
| M9 | Backlog opcional | Multi-tenancy real, re-ranking de busca, streaming de resposta (SSE), ingestão assíncrona, golden dataset para calibrar threshold de similaridade — já registrados como fora de escopo da v1 em `CLAUDE.md` e nos ADRs de `00-architecture.md` | 💤 Backlog |

> **Por que o M9 não é "o marco depois do M8"**: o número 9 já era usado como rótulo
> fixo do backlog opcional antes deste roadmap existir — está citado em `CLAUDE.md` e
> nos specs já fechados do M1/M2/M3 (`M1-design.md`, `M2-requirements.md`,
> `M3-design.md`) referindo-se sempre ao mesmo bucket de itens fora de escopo. Não é
> renumerado aqui para não reescrever histórico de marcos já concluídos por motivo
> estético — o M8 fica como slot reservado, não preenchido só para fechar a sequência.

## Ordem escolhida

M4 antes de M5/M6/M7 porque instrumentar cedo ajuda a validar as decisões dos
marcos seguintes com dados reais (custo por pergunta, latência) em vez de
suposição. M7 (deploy) fica por último porque expõe a aplicação publicamente —
faz mais sentido depois de M6 (rate limiting) estar em vigor. M8 fica em aberto
de propósito — só decidir o tema quando (ou se) surgir uma necessidade real.

Esta ordem não é definitiva: reavaliar depois de cada marco concluído, com
base no que se aprendeu e no que agrega mais para o portfólio.

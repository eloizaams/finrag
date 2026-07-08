---
name: explain-then-code
description: Aplica o fluxo do CLAUDE.md antes de implementar um conceito novo de um marco em src/ — especialmente conceitos de IA (embeddings, chunking, busca semântica/similaridade de cosseno, prompt de RAG), mas também outras decisões técnicas não triviais (JWT, hashing, etc). Explica o conceito, propõe a abordagem e só implementa em passos pequenos após confirmação explícita da autora. Use antes da primeira edição em src/ para uma tarefa nova de specs/MX-tasks.md. Não use para correções triviais, formatação, ou código já explicado/aprovado nesta mesma conversa.
disable-model-invocation: false
---

## Por que essa skill existe

`CLAUDE.md` deste projeto diz: "a autora escreve o código com orientação: não gere a
implementação inteira de um marco de uma vez. Explique o conceito primeiro
(principalmente os de IA — embeddings, chunking, busca semântica, prompt de RAG),
proponha a abordagem, e implemente em passos pequenos e revisáveis, para que ela
consiga defender cada decisão em entrevista." Esta skill é esse fluxo, passo a passo,
para não depender de lembrar disso a cada tarefa nova.

**Limite importante**: isto é uma skill, não um hook — ela guia o comportamento
quando é invocada (por você ou automaticamente por mim antes de mexer em `src/`), mas
não bloqueia edições por conta própria a nível de ferramenta. Se em algum momento for
importante ter um bloqueio garantido (ex.: impedir literalmente qualquer `Edit`/`Write`
em `src/` sem essa etapa ter rodado antes), isso precisaria de um hook `PreToolUse` em
`.claude/settings.json`, que é uma peça diferente — avise a autora se isso vier a ser
necessário, não implemente por conta própria.

## Sua tarefa

1. Identifique qual item de `specs/MX-tasks.md` está prestes a ser implementado (se
   não estiver claro pelo pedido, pergunte). Leia o trecho relevante de
   `MX-requirements.md` e `MX-design.md` para esse item.
2. Explique o conceito em linguagem simples, **antes de qualquer código**:
   - O que é, em 2-4 frases.
   - Por que é usado *neste* pipeline (RAG financeiro) — conecte com
     `specs/00-architecture.md` quando fizer sentido.
   - Trade-offs ou alternativas descartadas relevantes, para a autora poder defender
     a escolha em entrevista — se já estiverem no `MX-design.md`, resuma-as; se não,
     deixe claro que é uma decisão ainda em aberto.
3. Proponha a abordagem concreta de implementação (quais arquivos criar/alterar,
   mapeando para a Clean Architecture do projeto: domain → application →
   infrastructure/api), mas **não escreva código ainda**.
4. Pare e espere confirmação explícita da autora (algo como "pode implementar", "faz
   sentido", "vai", "bora") antes de tocar em `src/`.
5. Depois de confirmado, implemente em passos pequenos e revisáveis — um arquivo ou
   uma responsabilidade por vez, nunca o marco inteiro de uma vez. Após cada passo
   significativo, pause para ela revisar antes de seguir para o próximo.
6. Ao concluir cada passo, lembre de marcar o checkbox correspondente em
   `MX-tasks.md` — só marque depois de confirmar com ela que o passo está de fato
   completo e testado.

## Quando pular a explicação

Se o conceito já foi explicado e aprovado nesta mesma conversa (ex.: chunking já foi
explicado duas mensagens atrás e agora é só o próximo pedaço do mesmo conceito), não
repita a explicação inteira — confirme que é continuação do mesmo passo e siga com a
implementação incremental.

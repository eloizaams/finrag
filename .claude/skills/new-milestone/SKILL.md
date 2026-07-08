---
name: new-milestone
description: Gera o trio de specs de um novo marco (MX-requirements.md, MX-design.md, MX-tasks.md) no mesmo formato usado em M0/M1. Use ao começar a planejar um novo marco (M2, M3...), antes de escrever qualquer código.
disable-model-invocation: true
allowed-tools: Bash(ls specs*) Bash(find specs*) Read Write
---

## Specs existentes

!`ls specs/`

## Formato de referência

Os três arquivos de um marco seguem exatamente a estrutura de `specs/M1-requirements.md`,
`specs/M1-design.md` e `specs/M1-tasks.md` — leia os três antes de gerar o marco novo,
para copiar tom, nível de detalhe e nomes de seção.

### `MX-requirements.md`
- `# MX — <Nome do marco> — Requirements`
- `## Objetivo do marco` — parágrafo curto: o que o marco entrega e por que importa
  para o restante do pipeline (conectar com `specs/00-architecture.md`)
- `## Critérios de aceite` — lista numerada, testável/objetiva (verbos como "retorna",
  "responde", "cobre")
- `## Fora de escopo neste marco` — lista do que fica para depois; cruzar com a seção
  "Fora de escopo na v1" do `CLAUDE.md` para não reabrir o que já foi decidido como
  fora de escopo geral do projeto
- `## Perguntas em aberto (decidir no design.md)` — decisões técnicas ainda não
  tomadas; **não decidir aqui**, só listar as perguntas

### `MX-design.md`
- `# MX — <Nome do marco> — Design`
- `## Decisões técnicas` — tabela `| Item | Escolha | Justificativa |`, respondendo
  cada pergunta em aberto do requirements.md
- `## Onde entra cada peça (Clean Architecture)` — árvore de pastas
  `domain/ → application/ → infrastructure/ → api/`, com comentário de uma linha por
  arquivo (mesmo estilo do M1)
- Fluxos principais, passo a passo numerado
- Migration Flyway, se houver tabela nova (checar próximo número livre em
  `src/main/resources/db/migration/`)
- `## Dependências novas (build.gradle.kts)`, se houver
- `## Configuração (application.yaml)`, se houver
- `## Trade-offs conscientes` — o que foi deliberadamente simplificado e por quê

### `MX-tasks.md`
- Checklist agrupado por camada (`## Domínio e portas`, `## Casos de uso`,
  `## Infraestrutura — ...`, `## API`, `## Testes de integração`, `## Fechamento do
  marco`)
- Cada item mapeia para uma decisão do design.md ou critério do requirements.md
- `## Definição de pronto (Definition of Done)` no final, referenciando o
  requirements.md

## Sua tarefa

1. Se a autora não informou o número/nome/objetivo do marco, pergunte antes de gerar
   qualquer conteúdo.
2. Leia `specs/00-architecture.md` e o `CLAUDE.md` para garantir que o marco novo é
   coerente com a arquitetura geral e não invade a lista "Fora de escopo na v1".
3. Rascunhe primeiro só o `MX-requirements.md` (critérios de aceite + perguntas em
   aberto) e mostre para a autora revisar — **não escreva o arquivo ainda**, mostre o
   conteúdo no chat.
4. Depois de aprovado, escreva `MX-requirements.md` em `specs/`. Em seguida, decida
   junto com a autora as respostas de cada "pergunta em aberto" antes de gerar o
   design — não invente decisões técnicas sozinho, especialmente escolhas de
   biblioteca/algoritmo que ela precisa saber defender em entrevista.
5. Escreva `MX-design.md` com as decisões acordadas, e por último `MX-tasks.md`,
   derivando o checklist diretamente do design aprovado.
6. Confirme que os três arquivos existem em `specs/` com o padrão de nome
   `MX-requirements.md` / `MX-design.md` / `MX-tasks.md` (sem espaços, conforme
   `CLAUDE.md`) e sugira o commit semântico (ex.: `docs(specs): adiciona specs do
   M2`).

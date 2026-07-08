---
name: milestone-status
description: Varre specs/*-tasks.md, conta checkboxes marcados vs. total por marco, e cruza com o que já existe em src/ para mostrar quanto falta de cada marco. Use para ter uma visão rápida de progresso sem reler os specs inteiros.
disable-model-invocation: true
allowed-tools: Bash(find specs*) Bash(grep *) Bash(ls src*) Read Grep
---

## Checklists encontrados

!`for f in specs/*tasks.md; do total=$(grep -cE '^- \[[ xX]\]' "$f" 2>/dev/null); total=${total:-0}; feito=$(grep -cE '^- \[[xX]\]' "$f" 2>/dev/null); feito=${feito:-0}; echo "$f: $feito/$total"; done`

## Branch atual

!`git branch --show-current`

## Sua tarefa

1. Para cada arquivo `specs/*-tasks.md` listado acima, calcule a porcentagem
   concluída (`feito/total`).
2. Para os itens **desmarcados** (`- [ ]`) que citam um caminho de arquivo específico
   (ex.: `` `domain/model/User.kt` ``), verifique com `Read`/`find`/`Grep` se o
   arquivo já existe em `src/` — se existir, o checklist está desatualizado (avise
   para marcar o item).
3. Para os itens **marcados** (`- [x]`) que citam um arquivo, confirme que o arquivo
   ainda existe em `src/` — se não existir mais, avise (pode ter sido removido ou
   renomeado numa refatoração sem atualizar o checklist).
4. Monte uma tabela resumo, um marco por linha:

   | Marco | Requirements | Design | Tasks (feito/total) | % | Observação |
   |---|---|---|---|---|---|

   - "Requirements"/"Design" = ✅ se o arquivo correspondente existe em `specs/`,
     "—" se não existir.
   - "Observação" = discrepâncias encontradas nos passos 2/3, ou "em dia" se
     nenhuma foi encontrada.
5. Termine com uma linha destacando qual é o próximo item não feito do marco mais
   avançado que ainda está incompleto — o "próximo passo" óbvio para retomar o
   trabalho.
6. Esta skill é só leitura/diagnóstico — não edite nenhum arquivo.

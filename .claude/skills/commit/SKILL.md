---
name: commit
description: Cria um commit seguindo Conventional Commits e o fluxo de branches (git flow) deste projeto. Use ao finalizar uma tarefa/marco e quando o usuário pedir para commitar ou subir as mudanças.
disable-model-invocation: true
allowed-tools: Bash(git add *) Bash(git commit *) Bash(git status *) Bash(git diff *) Bash(git branch *) Bash(git checkout *) Bash(git push *) Bash(git log *)
---

## Estado atual

- Branch: !`git branch --show-current`
- Status: !`git status --short`
- Diff (resumo): !`git diff HEAD --stat`

## Fluxo de branches (git flow)

- `main`: sempre deployável, nunca commit direto
- `develop`: branch de integração, base de trabalho padrão
- `feature/<slug>`: uma por marco/tarefa (ex.: `feature/m1-auth-jwt`), sai de `develop`, volta para `develop`
- `release/<versao>`: preparação de release antes do merge em `main` (opcional, ao fechar um marco maior)
- `hotfix/<slug>`: correção urgente, sai de `main`, volta para `main` e `develop`

Antes de commitar, confira a branch atual. Se estiver em `main` ou `develop` com
mudanças que pertencem a uma tarefa específica, ofereça criar `feature/<slug>` antes
de commitar (`git checkout -b feature/<slug>`).

## Conventional Commits

Formato: `<tipo>(<escopo opcional>): <descrição no imperativo, minúsculo, sem ponto final>`

Tipos: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `style`, `perf`, `ci`, `build`

Exemplos:
- `feat(auth): adiciona login com JWT`
- `test(ingestion): cobre extração de PDF com Testcontainers`
- `chore: configura CI no GitHub Actions`

## Sua tarefa

1. Analise o diff acima e identifique o tipo de mudança predominante.
2. Se houver mudanças não relacionadas misturadas, avise e sugira separar em commits.
3. Proponha uma mensagem no formato acima (título + corpo curto se a mudança for complexa).
4. Peça confirmação antes de rodar `git add` e `git commit`.
5. Se a mudança corresponder a um item de `specs/MX-tasks.md`, lembre de marcar o
   checkbox correspondente antes do commit.
6. Não faça `git push` automaticamente — só depois de confirmação explícita.

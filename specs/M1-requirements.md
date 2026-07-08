# M1 — Auth JWT — Requirements

## Objetivo do marco

Permitir que um usuário se registre e faça login, recebendo um token JWT que
autentica as requisições aos endpoints de negócio (ingestão de documentos,
perguntas) que serão implementados nos marcos seguintes. Sem isso, M2/M3 não
têm como identificar "de quem" é um documento.

## Critérios de aceite

1. `POST /auth/register` cria um usuário novo a partir de `email` + `password`
   e retorna `201 Created` com os dados do usuário (sem a senha, nunca em texto
   plano nem hash, no corpo da resposta).
2. Registrar com um `email` já existente retorna `409 Conflict`, não `500`.
3. Validação de entrada: `email` precisa ter formato válido, `password` precisa
   ter tamanho mínimo (definir em design.md). Entrada inválida retorna `400
   Bad Request` com detalhes do campo que falhou.
4. `POST /auth/login` com credenciais corretas retorna `200 OK` com um token
   JWT (access token) e seu tempo de expiração.
5. `POST /auth/login` com email inexistente ou senha errada retorna `401
   Unauthorized`, com a **mesma mensagem genérica** nos dois casos (não revelar
   se o email existe).
6. Endpoints de negócio (qualquer rota fora de `/auth/**` e `/actuator/health`)
   exigem header `Authorization: Bearer <token>` válido; sem o header, retorna
   `401 Unauthorized`.
7. Token expirado, malformado ou com assinatura inválida também retorna `401
   Unauthorized`, sem stack trace vazando no corpo da resposta.
8. Senha é armazenada apenas como hash (nunca texto plano) e nunca aparece em
   logs da aplicação.
9. Segredo usado para assinar o JWT vem de variável de ambiente, nunca
   hardcoded nem commitado.
10. `GET /actuator/health` continua público (sem autenticação) — não pode
    quebrar o critério de aceite do M0.
11. Testes de integração (Kotest + Testcontainers, Postgres real) cobrem pelo
    menos: registro com sucesso, registro com email duplicado, login com
    sucesso, login com senha errada, acesso a rota protegida sem token, com
    token válido e com token expirado/inválido.

## Fora de escopo neste marco

- Refresh token / renovação de sessão sem novo login
- Fluxo de "esqueci minha senha" / reset de senha
- Verificação de email
- Papéis/roles (admin vs usuário comum) — existe só um tipo de usuário
- Login social / OAuth2 com provedor externo
- Rate limiting ou bloqueio de conta após tentativas falhas de login
- Isolamento de dados por usuário nos endpoints de documentos/perguntas — isso
  é responsabilidade do M2/M3 consumirem o `userId` do token, não deste marco

## Perguntas em aberto (decidir no design.md)

- Biblioteca para gerar/validar o JWT: implementação manual com `jjwt` ou
  `spring-boot-starter-oauth2-resource-server`?
- Algoritmo de assinatura: HS256 (simétrico) ou RS256 (assimétrico)?
- Tempo de expiração do access token?
- `register` já retorna um token (login automático) ou exige login separado?
- Onde entram as portas (`domain/port`) de hashing de senha e geração de token
  na Clean Architecture do projeto?

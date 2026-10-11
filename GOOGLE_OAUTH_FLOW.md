# Login Google e sessões

O frontend e a API vinculam cada autenticação ao navegador que iniciou o fluxo. O verifier do frontend fica em `sessionStorage`; a API mantém state, PKCE do Google e vinculação ao cookie em registros válidos por dez minutos.

## Início

O frontend gera 32 bytes aleatórios usando Web Crypto, codifica em Base64URL sem padding e calcula `BASE64URL(SHA256(verifier))`. Navega para:

```http
GET /auth/google/start?codeChallenge=<challenge-do-frontend>
```

A API registra o challenge e cria seu próprio verifier PKCE, um state aleatório e o cookie `CLIMB_OAUTH_BINDING` (HttpOnly, SameSite=Lax e Secure em HTTPS). Redireciona para o Google com state, `code_challenge` e `code_challenge_method=S256`.

A alternativa `GET /auth/google/url?codeChallenge=...` retorna a URL como JSON e define o mesmo cookie; o cliente deve preservá-lo. O aplicativo usa navegação direta para evitar dependência de cookies de terceiros em chamadas Ajax.

## Callback e troca

```http
GET /auth/google/callback?code=<codigo-google>&state=<state-original>
```

A API exige state não expirado e cookie correspondente, consome o state uma vez e troca o código com o verifier PKCE armazenado. Erros ou cancelamentos voltam ao frontend com uma mensagem humanizada.

Após autenticar uma conta autorizada, redireciona para o frontend com `google_oauth=success&code=<codigo-temporario>`. Cadastros aprovados que ainda precisam de perfil usam `google_oauth=completar_cadastro`. Contas pendentes/desativadas seguem as regras de aprovação existentes.

O código temporário expira em 60 segundos e sua troca exige o verifier original do frontend:

```http
POST /auth/exchange
Content-Type: application/json

{"code":"<codigo-temporario>","codeVerifier":"<verifier-original-do-frontend>"}
```

A resposta usa o envelope `success`, `data`, `message` e `timestamp`. Os dados incluem os tokens da aplicação e o usuário; dados de integração Google dependem da configuração da agenda. A API compara o hash do verifier e bloqueia um segundo consumo do código com lock no banco. Tokens não são enviados na URL de redirecionamento.

O frontend compartilha trocas concorrentes do mesmo código e remove o verifier após sucesso. Para completar o cadastro, usa `POST /usuarios/completar-cadastro` com o token de cadastro no header Authorization.

## Renovação e encerramento

```http
POST /auth/refresh
Content-Type: application/json

{"refreshToken":"<refresh-atual>"}
```

`data` contém `accessToken`, `refreshToken` e `expiresIn` em milissegundos. O cliente substitui os dois tokens. Cada renovação rotaciona o refresh, sem prolongar a validade original da sessão. O servidor guarda somente o hash do refresh na tabela de sessões. Reutilizar um refresh anterior encerra essa sessão.

O frontend agrupa renovações simultâneas e usa Web Locks, quando disponível, para coordenar abas. O token é lido e salvo dentro do lock. Uma resposta atrasada de refresh não restaura uma sessão encerrada no mesmo navegador.

```http
POST /auth/logout
Content-Type: application/json

{"refreshToken":"<refresh-da-sessao>"}
```

O logout retorna 204 e revoga a sessão no servidor. Access tokens somente autorizam rotas privadas enquanto sua sessão estiver ativa e o usuário continuar ativo. Alterações de senha/e-mail e mudanças de situação invalidam tokens anteriores, mesmo após reativação da conta.

## Configuração

Consulte [README.md](README.md#configuração-e-atualização-de-segurança) para JWT_SECRET, issuer, audience, origens CORS e migrations V70/V71. Backend e frontend devem ser atualizados juntos. Tokens anteriores à atualização exigem novo login.

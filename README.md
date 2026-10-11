# Climb Services API

API REST construída com Spring Boot 4 e Java 25.

## Pré-requisitos

| Dependência | Versão mínima |
|---|---|
| Java (OpenJDK) | 25 |

O Maven não precisa ser instalado separadamente — o projeto inclui o Maven Wrapper (`mvnw`).

## Dependências do projeto

Gerenciadas automaticamente pelo Maven via `pom.xml`:

| Dependência | Descrição |
|---|---|
| `spring-boot-starter-webmvc` | Framework web (Tomcat embutido + Spring MVC) |
| `spring-boot-starter-validation` | Validação de beans (Jakarta Validation) |
| `spring-boot-starter-actuator` | Endpoints de monitoramento |
| `spring-boot-devtools` | Restart automático durante desenvolvimento |
| `lombok` | Redução de boilerplate |

## Comandos

### Compilar

```bash
./mvnw compile
```

### Executar testes

```bash
./mvnw test
```

### Rodar localmente

```bash
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

Para usar uma porta diferente:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

### Rodar com Docker Compose

Para garantir que o container use o código mais recente:

```bash
docker compose build --no-cache climb-api
docker compose up -d --force-recreate climb-api
```

### Gerar o JAR

```bash
./mvnw package -DskipTests
java -jar target/climb-api-0.0.1-SNAPSHOT.jar
```

### Parar a aplicação

Pressione `Ctrl+C` no terminal. Se a porta continuar ocupada:

```bash
lsof -ti:8080 | xargs kill -9
```

## Endpoints

| Método | URL | Descrição |
|---|---|---|
| `POST` | `/auth/login` | Autentica usuário com `email` e `senha` |
| `POST` | `/auth/refresh` | Renova token de acesso |
| `GET` | `/hello` | Endpoint de teste |
| `GET` | `/actuator/health` | Status da aplicação |
| `GET` | `/actuator/info` | Informações da aplicação |
| `GET` | `/actuator/metrics` | Métricas disponíveis |

### Exemplo de login

```bash
curl -X POST http://localhost:8080/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"usuario@exemplo.com","senha":"123456"}'
```

### Solução rápida para `404` em `/auth/login`

Se `/hello` responde e `/auth/login` retorna `404`, normalmente o container está com imagem antiga.

1. Rebuild da API sem cache.
2. Recrie o container da API.
3. Teste novamente com `POST /auth/login` e corpo JSON.

## Google Calendar OAuth

Defina estas variáveis no `.env` para habilitar o fluxo:

```env
GOOGLE_CALENDAR_CLIENT_ID=...
GOOGLE_CALENDAR_CLIENT_SECRET=...
GOOGLE_CALENDAR_REDIRECT_URI=http://localhost:8080/auth/google/callback
GOOGLE_CALENDAR_FRONTEND_URL=http://localhost:5173
```

Endpoints adicionais:

- `GET /auth/google/start?codeChallenge=...`
- `GET /auth/google/url?codeChallenge=...` (alternativa que retorna JSON e exige preservação do cookie)
- `GET /auth/google/callback`
- `POST /auth/exchange`

Fluxo:

1. O frontend gera um verifier aleatório, guarda-o no `sessionStorage` e calcula seu challenge SHA-256 em Base64URL.
2. Navega para `GET /auth/google/start?codeChallenge=...`. A API registra um `state` de uso único, seu próprio PKCE e um cookie HttpOnly de vinculação ao navegador.
3. O Google retorna para `/auth/google/callback` com `code` e `state`. O backend valida o state e o cookie antes de trocar o código com seu verifier PKCE.
4. A API redireciona para `GOOGLE_CALENDAR_FRONTEND_URL` somente com um código temporário de troca, válido por 60 segundos.
5. O frontend chama `POST /auth/exchange` com `{ "code": "...", "codeVerifier": "..." }`. A API exige o verifier original e consome o código uma única vez.

## Configuração e atualização de segurança

`JWT_SECRET` é obrigatório: use uma chave aleatória com pelo menos 256 bits. A aplicação rejeita uma chave curta ou o antigo valor padrão. `JWT_ISSUER` e `JWT_AUDIENCE` devem corresponder à aplicação; ambos usam `climb-api` por padrão.

Configure `CORS_ALLOWED_ORIGINS` com as origens completas dos frontends, separadas por vírgula, sem curingas. Na ausência dessa variável, a API usa `APP_FRONTEND_URL` e `GOOGLE_CALENDAR_FRONTEND_URL` (localhost no desenvolvimento).

Atualize backend e frontend juntos e aplique as migrations V70 e V71 pelo Flyway antes de validar o schema. Tokens anteriores à atualização são rejeitados; os usuários precisam entrar novamente.

`POST /auth/refresh` retorna `accessToken`, um novo `refreshToken` e `expiresIn` em milissegundos. Substitua os dois tokens a cada renovação. Reutilizar um refresh anterior revoga a sessão inteira. `POST /auth/logout`, com o refresh token no corpo, encerra a sessão no servidor. Trocar senha ou e-mail e revogar o usuário invalida suas sessões anteriores.

A gestão de usuários e permissões exige `PERMITIR_ACESSO`. Uploads gerais exigem `ARQUIVO_UPLOAD`; o envio interno de documentos exige `DOCUMENTO_JURIDICO_EDITAR`. As permissões `PLANILHA_LER`, `PLANILHA_CRIAR`, `PLANILHA_EDITAR`, `PLANILHA_DELETAR` e `PLANILHA_CONFIGURAR_ACESSO` são independentes e também exigem acesso ao contrato vinculado. As novas permissões devem ser atribuídas pelo administrador; as migrations não concedem acesso automaticamente.

## Aprovação de cadastros Google

A aprovação cria o usuário com cargo e permissões e situação `COMPLETAR_CADASTRO`.
Ele já aparece em Permissões e na gestão de acessos. No primeiro login Google,
o token de cadastro permite abrir `/first-access` e preencher CPF, contato e senha.
A conclusão ativa o mesmo usuário e preserva alterações de cargo e permissões
feitas pelo administrador depois da aprovação.

Usuários ativos existentes têm prioridade sobre solicitações Google antigas.
Aprovações permanecem válidas após o prazo original da solicitação; o token
temporário para concluir o cadastro continua sujeito à própria expiração.

A publicação desta correção deve incluir backend, frontend e a migration
`V55__provision_approved_google_users.sql`. Ela permite CPF nulo durante o primeiro
acesso e recupera cadastros aprovados ainda sem usuário, preservando contas
existentes. No perfil `prod`, o Flyway aplica migrations na inicialização por
padrão; com `SPRING_FLYWAY_ENABLED=false`, aplique a migration antes de liberar
a nova versão da API.

## Estrutura do projeto

```
src/main/java/com/climb/api/
├── ClimbApiApplication.java        # Entry point
├── config/
│   └── WebConfig.java              # Configuração de CORS
├── controller/
│   └── HelloController.java        # REST controller
├── model/dto/
│   └── ApiResponse.java            # Wrapper padrão de resposta
└── service/
    └── HelloService.java           # Camada de serviço
```

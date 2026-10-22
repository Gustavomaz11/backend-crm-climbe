# Tokens e Integração

O menu fica em **Configurações → Tokens e Integração**. A permissão `INTEGRACOES_GERENCIAR` controla todas as ações administrativas. A migration V82 concede essa permissão aos usuários que já possuem `PERMITIR_ACESSO`; também pode ser atribuída na tela de Permissões.

## Cadastro e entrega

Informe plataforma/empresa, CNPJ (numérico ou alfanumérico com dígitos verificadores), representante e e-mail. Selecione um módulo, os métodos HTTP, as operações e as empresas cujos dados podem ser acessados. Informe validade e limite por minuto. Uma integração pode acessar várias empresas explicitamente autorizadas; o CNPJ do destinatário não define o escopo dos dados.

O cadastro enfileira um e-mail com Client ID, documentação PDF e link de ativação. O link usa o fragmento da URL, expira em até 24 horas e só é consumido quando o destinatário clica em **Ativar e mostrar credenciais**. O segredo aleatório de 256 bits é criado nessa etapa, exibido uma vez e armazenado apenas como SHA-256. Não aparece em listagens, logs, PDF ou reenvios.

O processamento usa o SMTP já configurado, sem manter transação de banco durante o envio. Faz até oito tentativas com intervalo crescente. A tela mostra o status de aceitação pelo SMTP e permite reenvio. Reenviar uma integração já ativa envia somente a documentação; um segredo perdido exige rotação. A página de ativação não envia telemetria nem persiste credenciais no navegador.

## API externa v1

Os endpoints externos ficam em `/api/integracoes/v1` e são separados das rotas da aplicação. O catálogo administrativo `GET /integracoes/catalogo` é derivado das rotas anotadas e também gera o PDF personalizado. Uma nova rota não recebe acesso nas integrações existentes automaticamente.

- Técnico: listar contratos autorizados, consultar o Kanban, criar, editar, mover e excluir tarefas.
- Comercial: consultar, criar, editar e excluir propostas; alterar valuation; consultar, criar, editar e mover negócios entre etapas abertas.
- Jurídico: consultar, criar, editar e excluir contratos; aprovar/rejeitar; movimentar preparação; baixar PDF.

Essas são as operações publicadas na primeira versão da API externa. Rotas internas de administração, usuários e outras operações não catalogadas continuam indisponíveis para integrações. Edição genérica não permite aprovar contratos ou mover tarefas/negócios: cada ação possui autorização explícita. As regras e permissões de negócio do usuário criador continuam sendo verificadas pelos serviços existentes. As respostas usam projeções próprias, sem expor entidades completas ou o catálogo global de usuários.

Listagens filtram as empresas no banco e recebem `pagina` (inicia em 0) e `tamanho` (1–100; padrão 50). Registros fora do escopo respondem 404. A consulta individual e cada alteração validam o escopo antes de executar o serviço, incluindo vínculos entre empresas, propostas e negócios.

## Autenticação

```sh
curl -X POST 'https://API_PUBLICA/api/integracoes/oauth/token' \
  -u 'CLIENT_ID:CLIENT_SECRET' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'grant_type=client_credentials'
```

Também aceita `client_id` e `client_secret` no formulário, sem combinar as duas formas. Retorna `access_token`, `token_type=Bearer`, `expires_in` e `scope`. Não emite refresh token. Envie `Authorization: Bearer ACCESS_TOKEN` nas requisições. Em produção, use HTTPS.

O token tem duração máxima de 15 minutos, limitada à validade da integração. Sua assinatura, emissor, audiência, tipo e versão são independentes dos tokens de usuários. A cada requisição são conferidos status, validade, usuário criador ativo, versão, módulo, método e operação. Suspensão, revogação, edição de permissões e rotação invalidam os tokens existentes; reativação não restaura tokens antigos. A rotação também invalida o segredo e exige nova ativação. Revogação é permanente.

O limite por integração é persistido e compartilhado entre instâncias e tokens, incluindo a emissão de tokens. Há proteção adicional de 30 tentativas por minuto/IP nas rotas públicas, por instância; configure também o limite de entrada no proxy em ambientes com múltiplas instâncias. O IP vem do endereço remoto informado pelo servidor; como o projeto utiliza forwarded headers, o proxy deve limpar os headers de origem recebidos da internet e informar o IP confiável. Respostas 429 incluem `Retry-After`. As últimas 100 entradas de auditoria podem ser consultadas na tela; o banco preserva o histórico completo sem corpos de requisição, tokens ou segredos.

## Configuração e execução

Aplique **V82__tokens_integracoes.sql** pelo Flyway antes de iniciar esta versão. No perfil `prod`, o Flyway executa na inicialização por padrão. Em desenvolvimento, ou com `SPRING_FLYWAY_ENABLED=false`, rode o serviço de migrations (`docker compose run --rm flyway`, com o banco disponível) antes de reiniciar o backend. Atualize também o frontend. Configure:

```dotenv
INTEGRACOES_API_URL=https://URL_PUBLICA_DO_BACKEND
INTEGRACOES_EMAIL_ENABLED=true
INTEGRACOES_EMAIL_INTERVALO_MS=5000
# Opcional: chave exclusiva aleatória com no mínimo 32 caracteres.
# INTEGRACOES_CHAVE=...
```

`APP_FRONTEND_URL` deve apontar para o frontend público para que o link do e-mail funcione. As configurações SMTP existentes continuam sendo usadas. Se `INTEGRACOES_CHAVE` estiver ausente, utiliza uma chave derivada de `jwt.secret`, com separação de domínio. Alterar a chave invalida links e tokens em circulação. Os testes usam H2, SMTP simulado e dados fictícios; não enviam e-mails reais.

As listagens, campos e métodos disponíveis são definidos pelo catálogo; o PDF é um retrato das permissões no momento da geração. Edição de permissões enfileira uma versão atualizada da documentação.

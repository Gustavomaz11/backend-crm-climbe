# Convites para o CRM

O menu **Configurações → Convite** permite que usuários com `PERMITIR_ACESSO`
informem até 100 destinatários por lote. Aceita endereços separados por linha,
vírgula, ponto e vírgula ou espaço. Endereços repetidos no lote são ignorados,
considerando maiúsculas e minúsculas equivalentes.

`POST /convites` exige autenticação e `PERMITIR_ACESSO`:

```json
{"emails":["socio@empresa.com.br","outra.pessoa@empresa.com.br"]}
```

Responde `202 Accepted` com `agendados` e `duplicadosIgnorados`. Um endereço
inválido ou um lote acima de 100 entradas rejeita a requisição inteira. O lote
validado é persistido em uma única transação na fila `emails_notificacao_pendentes`.
Cada destinatário recebe uma mensagem individual. O agendador existente envia os
e-mails e retoma tentativas após falhas de SMTP. A resposta da API confirma o
agendamento, não a entrega na caixa de entrada.

O e-mail tem assunto **Bem-vindo ao CRM da Climbe!**, dá as boas-vindas ao sócio e
explica o acesso pelo Google e a necessidade de aprovação. Seu botão aponta para
`APP_FRONTEND_URL/convite`. Essa página inicia o fluxo Google existente, gerando e
armazenando o verifier PKCE no navegador antes de navegar para
`/auth/google/start`. Os callbacks continuam usando as rotas existentes.

Enviar um convite não cria usuário, não concede permissões e não aprova cadastro.
Pessoas sem conta autorizada entram no fluxo normal de solicitação de acesso.

Publique backend e frontend juntos. Não há nova migration. Configure
`APP_FRONTEND_URL` com o endereço público do frontend, inclusive em produção
(por exemplo, `https://www.crmclimbe.com.br`), e mantenha as configurações SMTP e
OAuth já utilizadas pelo sistema.

## Avisos de solicitação e aprovação

Ao registrar uma nova solicitação, por Google ou pelo cadastro com e-mail e senha,
o sistema grava um aviso individual para cada administrador ativo. O aviso aparece
no sininho e no popup de notificações, com nome e e-mail do solicitante, e abre
`/aprovar-acesso`. Consultar novamente um cadastro Google já pendente não gera
outra solicitação nem outro aviso.

Atualmente os grupos são modelos de permissões: aplicar um grupo copia seus itens
para o usuário. Por isso, o destinatário administrador é o usuário `ATIVO` que
possui todas as permissões do grupo ativo chamado `Administrador`, podendo também
ter permissões extras. Um grupo vazio ou inativo não seleciona destinatários.
Possuir somente `PERMITIR_ACESSO` não equivale a possuir todo o grupo Administrador.

Ao aprovar, o e-mail do solicitante é colocado na fila na mesma transação da
decisão. A mensagem informa que o acesso ao CRM foi aprovado e que as atividades
já podem começar. Cadastros Google recebem o link `/convite`, que inicia a
autenticação Google, e orientação para concluir o primeiro cadastro, quando
necessário. Cadastros com senha recebem o link para o login padrão.

Recusas, exclusões, tentativas repetidas de aprovação e operações desfeitas não
geram e-mail de aprovação. O envio usa as novas tentativas da fila existente,
sem esperar o SMTP durante a aprovação. Os avisos dos administradores ficam
somente no sistema. Esta atualização não exige migration.

# Limpeza pontual da base

`scripts/reset_database.py` é uma ferramenta manual, independente do Flyway e da
inicialização da aplicação. Lê o datasource do `.env` informado e preserva:

- A conta `gustavo@climbe.com.br`, com os dados de login e vínculos Google.
- Cargos, hierarquia, grupos de cargos, grupos de permissões e seus itens.
- Catálogo de permissões e histórico técnico do Flyway.

Todos os outros dados da base são removidos, incluindo sessões, solicitações de
acesso, cadastros Google pendentes ou aprovados, integrações e filas de e-mail.
O administrador recebe todas as permissões do catálogo. Sua versão de autenticação
é incrementada, exigindo novo login. Os IDs não são reiniciados, para evitar que
links e tokens antigos identifiquem registros novos.

O script verifica o schema e a conta antes de agir. Com `--apply`, adquire locks
de escrita, gera um backup SQL completo comprimido fora do repositório, confere
o arquivo, executa as exclusões em transação e verifica os catálogos preservados
e as tabelas esvaziadas antes do commit. Em caso de falha, desfaz a transação.
A limpeza se limita ao banco; arquivos e eventos nos provedores externos não são
excluídos por essa ferramenta.

Dependências opcionais da ferramenta:

```powershell
python -m pip install pymysql python-dotenv
```

Inspeção sem alterações:

```powershell
python scripts/reset_database.py --env-file .env
```

Execução pontual, após conferir a base indicada no `.env`:

```powershell
python scripts/reset_database.py --env-file .env --apply --backup-dir C:/backups/climbe
```

O backup `.sql.gz` contém schema e dados anteriores à limpeza, incluindo dados
pessoais e credenciais armazenadas. Mantenha-o em armazenamento privado. Para
restauração, descompacte e importe pelo cliente MySQL na base desejada, com a API
parada; a importação substitui as tabelas existentes.

O uso de transação com `LOCK TABLES` segue a
[documentação do MySQL](https://dev.mysql.com/doc/refman/9.7/en/lock-tables.html).
Não use `BEGIN` após adquirir os locks, pois ele os libera.

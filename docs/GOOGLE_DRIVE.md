# Pastas de empresas e serviços no Google Drive

A pasta raiz padrão é `1Ea1yJuqRd7jfSAa_aC58qyl_Dsv9qmVB`. A integração usa a conta definida em `SPRING_MAIL_USERNAME`, com o vínculo OAuth Google salvo pelo Climbe. A senha SMTP não é usada para acessar o Drive.

1. Ative a **Google Drive API** no mesmo projeto Google Cloud do cliente OAuth.
2. A conta de `SPRING_MAIL_USERNAME` precisa estar cadastrada e ativa no Climbe e ter acesso de edição à pasta raiz.
3. Entre no Climbe pelo Google com essa conta e conceda a permissão do Drive. Vínculos anteriores precisam renovar a autorização para incluir `https://www.googleapis.com/auth/drive`. A integração reutiliza o token de atualização armazenado de forma criptografada.
4. Execute a migration `V77__drive_empresas_servicos.sql` pelo fluxo Flyway do ambiente e reinicie a API para carregar a configuração. Nos ambientes que usam o container Flyway, execute `docker compose run --rm flyway`; quando a aplicação gerencia as migrations, elas são aplicadas na inicialização.

Para uma credencial independente do login, configure `GOOGLE_DRIVE_REFRESH_TOKEN`, `GOOGLE_DRIVE_CLIENT_ID` e `GOOGLE_DRIVE_CLIENT_SECRET`. O token precisa pertencer à conta com acesso à pasta e ter o escopo Drive. Os campos de cliente usam os valores de `GOOGLE_CALENDAR_CLIENT_ID` e `GOOGLE_CALENDAR_CLIENT_SECRET` por padrão. `GOOGLE_DRIVE_ACCOUNT_EMAIL` permite substituir a conta de `SPRING_MAIL_USERNAME`.

## Funcionamento

- Os cadastros de empresa (inclusive pelo comercial) e as edições enfileiram a criação/atualização de `<Nome fantasia>-<CNPJ com máscara>` e da subpasta `Documentações`. Na ausência de nome fantasia, usa-se a razão social.
- Os PDFs disponíveis do CNPJá são enviados para `Documentações`. O conteúdo é preservado no banco e uma falha no Drive não exige uma nova emissão no CNPJá.
- A aprovação pelo cliente, confirmada pela assinatura ZapSign, enfileira uma pasta para cada serviço da proposta/contrato. A aprovação interna do jurídico não cria pastas de serviços.
- Cada combinação empresa/serviço possui uma única pasta, compartilhada por suas tarefas e contratos. Cada serviço do contrato possui seu próprio Kanban no menu, como `Datawer Tec - Contabilidade` e `Datawer Tec - CFO`. As tarefas acessam a pasta do serviço do seu quadro.
- A separação dos quadros requer `V78__kanban_por_servico.sql`. Colunas e tarefas anteriores ficam no primeiro serviço da proposta, preservando IDs, histórico, comentários e anexos; os demais serviços iniciam seus próprios quadros.
- Ao ganhar uma venda com vários serviços, é obrigatório selecionar um responsável técnico para cada serviço da proposta aprovada. A mesma pessoa pode ser escolhida em mais de um serviço. A migração `V79__responsaveis_por_servico.sql` salva esses vínculos; contratos anteriores continuam usando o responsável já cadastrado. Cada quadro exibe seu gestor e direciona a ele os avisos de tarefas e prazos. Cada responsável configura a equipe do seu serviço e recebe acesso após a assinatura.
- A lista do Kanban consulta o Drive diretamente a cada 15 segundos e ao atualizar a tela. Criações e uploads invalidam a lista de todas as tarefas abertas. Pastas criadas fora do sistema aparecem na navegação, inclusive subpastas.
- PDFs e imagens podem ser visualizados no sistema; outros arquivos podem abrir pelo link do Drive. Downloads passam pela API autenticada. Documentos Google são exportados para DOCX, XLSX ou PPTX.
- Arquivos antigos de tarefas continuam disponíveis para consulta em **Arquivos anteriores da tarefa**. Comentários e seus anexos permanecem vinculados à tarefa.

O processamento da fila inicia após o commit do cadastro e roda a cada 5 segundos em um scheduler próprio. Falhas de acesso ficam registradas em `empresa_drive.erro` e são tentadas novamente após 1 minuto. As propriedades privadas dos arquivos no Drive permitem reutilizar pastas e comprovantes após uma interrupção. Não são concedidas permissões públicas.

`GOOGLE_DRIVE_ENABLED=false` pausa o processamento. As solicitações permanecem no banco para quando a integração for ativada. Empresas antigas entram na fila ao serem editadas ou ao gerarem novos comprovantes; esta alteração não faz uma migração automática de todos os arquivos históricos.

## Equipes e permissões automáticas por serviço

A migração `V81__equipe_permissoes_drive_por_servico.sql` cria a equipe por serviço em `contrato_equipe_servico`. O botão **Gerenciar equipe do serviço** atua apenas no quadro aberto. A liderança de outro serviço não concede permissão para alterar essa equipe, suas tarefas ou seus arquivos.

Após a assinatura, a pasta de cada serviço usa acesso limitado no Google Drive. São autorizados o responsável técnico, a equipe fixa e os apoios ainda atribuídos a tarefas pendentes desse serviço, desde que o usuário esteja ativo e tenha e-mail no domínio permitido. A conclusão/remoção da última tarefa de apoio encerra seu acesso, quando ele não possui outro vínculo válido. O encerramento do serviço também remove os acessos vinculados a ele. Se a mesma empresa possui vários contratos ativos para o mesmo serviço, a pasta usa a união das equipes: sair de um contrato não remove o acesso concedido por outro.

O padrão é `@climbe.com.br`, comparado como domínio exato, com permissão de edição (`writer`). Não é necessário adicionar variáveis para usar esses padrões. Opcionalmente, `GOOGLE_DRIVE_ALLOWED_DOMAINS` aceita uma lista separada por vírgulas e `GOOGLE_DRIVE_SERVICE_ROLE` pode ser `reader` ou `writer`. As permissões do CRM também são verificadas nos downloads e uploads; a conta técnica não permite contornar a equipe pelo backend.

As alterações de equipe, tarefas, usuários e contratos enfileiram a revisão após o commit, em um processamento separado a cada 5 segundos. As pastas existentes entram na fila na migração; uma auditoria a cada hora também corrige alterações feitas diretamente no Drive. Falhas são tentadas novamente após 1 minuto, com o motivo em `empresa_drive_servicos.permissoes_erro` e aviso na navegação de arquivos do Kanban. O último sucesso fica em `permissoes_sincronizadas_em`.

A revisão bloqueia a herança de acesso na pasta do serviço, remove compartilhamentos diretos com pessoas fora da equipe, grupos, domínio inteiro e links públicos, e revisa também os arquivos e subpastas internos. A raiz geral, a pasta da empresa e `Documentações` não são compartilhadas automaticamente. No Meu Drive, a pasta do serviço impede que editores alterem seu compartilhamento. As exceções de propriedade e administração do Google continuam válidas: proprietários e gestores de drives compartilhados mantêm acesso. A conta de integração precisa ter capacidade de gerenciar o compartilhamento e limitar a herança; sem essas capacidades, a sincronização registra erro.

Na atualização de contratos antigos com um único serviço, todos os membros da equipe anterior são preservados no serviço. Nos contratos com vários serviços, são transferidos automaticamente os responsáveis técnicos e os membros cuja atribuição a tarefas identifica o serviço. Os vínculos anteriores completos permanecem na tabela original `contrato_equipe`; pessoas sem serviço identificável precisam ser selecionadas pelo responsável no quadro correto. Isso evita conceder acesso ao CFO e à Contabilidade por um vínculo antigo genérico.

Referências de permissões: [acesso limitado](https://developers.google.com/workspace/drive/api/guides/limited-expansive-access), [compartilhamento e herança](https://developers.google.com/workspace/drive/api/guides/manage-sharing).

Referências: [pastas](https://developers.google.com/workspace/drive/api/guides/folder), [uploads](https://developers.google.com/workspace/drive/api/guides/manage-uploads), [downloads e exportação](https://developers.google.com/workspace/drive/api/guides/manage-downloads).

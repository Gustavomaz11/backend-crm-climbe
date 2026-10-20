# Pastas de empresas e serviços no Google Drive

A pasta raiz padrão é `1Ea1yJuqRd7jfSAa_aC58qyl_Dsv9qmVB`. A integração usa a conta definida em `SPRING_MAIL_USERNAME`, com o vínculo OAuth Google salvo pelo Climbe. A senha SMTP não é usada para acessar o Drive.

1. Ative a **Google Drive API** no mesmo projeto Google Cloud do cliente OAuth.
2. A conta de `SPRING_MAIL_USERNAME` precisa estar cadastrada e ativa no Climbe e ter acesso de edição à pasta raiz.
3. Entre no Climbe pelo Google com essa conta e conceda a permissão do Drive. Vínculos anteriores precisam renovar a autorização para incluir `https://www.googleapis.com/auth/drive`. A integração reutiliza o token de atualização armazenado de forma criptografada.
4. Execute a migration V75 pelo fluxo Flyway do ambiente e reinicie a API para carregar a configuração. Nos ambientes que usam o container Flyway, execute `docker compose run --rm flyway`; quando a aplicação gerencia as migrations, elas são aplicadas na inicialização.

Para uma credencial independente do login, configure `GOOGLE_DRIVE_REFRESH_TOKEN`, `GOOGLE_DRIVE_CLIENT_ID` e `GOOGLE_DRIVE_CLIENT_SECRET`. O token precisa pertencer à conta com acesso à pasta e ter o escopo Drive. Os campos de cliente usam os valores de `GOOGLE_CALENDAR_CLIENT_ID` e `GOOGLE_CALENDAR_CLIENT_SECRET` por padrão. `GOOGLE_DRIVE_ACCOUNT_EMAIL` permite substituir a conta de `SPRING_MAIL_USERNAME`.

## Funcionamento

- Os cadastros de empresa (inclusive pelo comercial) e as edições enfileiram a criação/atualização de `<Nome fantasia>-<CNPJ com máscara>` e da subpasta `Documentações`. Na ausência de nome fantasia, usa-se a razão social.
- Os PDFs disponíveis do CNPJá são enviados para `Documentações`. O conteúdo é preservado no banco e uma falha no Drive não exige uma nova emissão no CNPJá.
- A aprovação pelo cliente, confirmada pela assinatura ZapSign, enfileira uma pasta para cada serviço da proposta/contrato. A aprovação interna do jurídico não cria pastas de serviços.
- Cada combinação empresa/serviço possui uma única pasta, compartilhada por suas tarefas e contratos. Na tarefa de um contrato com vários serviços, o seletor mostra os serviços daquele contrato.
- A lista do Kanban consulta o Drive diretamente a cada 15 segundos e ao atualizar a tela. Criações e uploads invalidam a lista de todas as tarefas abertas. Pastas criadas fora do sistema aparecem na navegação, inclusive subpastas.
- PDFs e imagens podem ser visualizados no sistema; outros arquivos podem abrir pelo link do Drive. Downloads passam pela API autenticada. Documentos Google são exportados para DOCX, XLSX ou PPTX.
- Arquivos antigos de tarefas continuam disponíveis para consulta em **Arquivos anteriores da tarefa**. Comentários e seus anexos permanecem vinculados à tarefa.

O processamento da fila inicia após o commit do cadastro e roda a cada 5 segundos em um scheduler próprio. Falhas de acesso ficam registradas em `empresa_drive.erro` e são tentadas novamente após 1 minuto. As propriedades privadas dos arquivos no Drive permitem reutilizar pastas e comprovantes após uma interrupção. Não são concedidas permissões públicas.

`GOOGLE_DRIVE_ENABLED=false` pausa o processamento. As solicitações permanecem no banco para quando a integração for ativada. Empresas antigas entram na fila ao serem editadas ou ao gerarem novos comprovantes; esta alteração não faz uma migração automática de todos os arquivos históricos.

Referências: [pastas](https://developers.google.com/workspace/drive/api/guides/folder), [uploads](https://developers.google.com/workspace/drive/api/guides/manage-uploads), [downloads e exportação](https://developers.google.com/workspace/drive/api/guides/manage-downloads).

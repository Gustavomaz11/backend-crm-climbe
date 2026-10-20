# Encerramento de serviços no Kanban

O botão **Encerrar Contrato** encerra somente o serviço do quadro aberto. Apenas seu responsável técnico pode confirmar a ação; responsáveis de outros serviços, membros da equipe e leitores administrativos recebem HTTP 403 se tentarem pela API.

`PATCH /contratos/{contratoId}/kanban/encerrar` recebe `{ "servico": "CFO" }` e usa exclusivamente o usuário da sessão autenticada. Em contratos com vários serviços, o serviço é obrigatório. Contratos antigos sem serviço identificado também podem ser encerrados.

A migração **V80** cria `contrato_servicos_encerrados`, com serviço, data e responsável pelo encerramento. A operação é transacional e idempotente, com bloqueio do contrato compartilhado pelas alterações do Kanban. A assinatura mantém seu status `APROVADO`; o encerramento operacional é registrado por serviço.

O quadro encerrado deixa de aparecer nas listas e não permite novas alterações nas tarefas. Colunas, tarefas, comentários, arquivos do Drive e registros anteriores permanecem armazenados. Os demais serviços continuam ativos. Se todos forem encerrados, o contrato sai inteiramente do painel. O encerramento não cancela cobranças nem altera parcelas.

Os períodos abertos de apoio desse serviço são encerrados, preservando a participação histórica. Essas tarefas deixam de conceder acesso temporário e de gerar novos alertas de prazo. Um sinal SSE `kanban`, enviado após o commit, atualiza a lista e o acesso ao painel nas abas conectadas; a reabertura do sistema também sincroniza esses dados.

Ao criar uma tarefa ou adicionar responsáveis, cada novo responsável recebe uma notificação persistida com link para a tarefa e o serviço correspondente. Editar a tarefa mantendo as mesmas pessoas não duplica os avisos. O sinal SSE atualiza o sininho e o acesso ao Kanban do destinatário; quem estiver offline verá os avisos no próximo acesso.

A criação não envia um aviso extra de tarefa criada ao gestor ou a quem atribuiu a tarefa. O aviso de atribuição só é enviado às pessoas selecionadas como responsáveis; o gestor também o recebe se estiver entre elas. Esta regra vale para novas atribuições e preserva o histórico de notificações já gravadas.

Para publicar, suba primeiro o backend com as migrações e depois o frontend. Esta alteração não exige novas variáveis no `.env`.

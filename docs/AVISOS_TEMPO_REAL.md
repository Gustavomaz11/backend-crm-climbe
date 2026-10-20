# Avisos em tempo real

O frontend abre `GET /avisos/stream` com o mesmo `Authorization: Bearer` das APIs. O canal SSE sinaliza mudanças em notificações e convites do usuário autenticado; não recebe um ID de usuário como parâmetro e não transporta conteúdo dos avisos.

O sinal `kanban` sincroniza a lista de quadros e a permissão do menu ao atribuir tarefas ou encerrar serviços. No encerramento, todas as abas conectadas recebem somente o sinal, sem IDs ou conteúdo do contrato; cada aba consulta novamente os dados permitidos ao seu usuário. `sincronizar` também reconcilia esses caches após reconexões. O encerramento por serviço exige a migração V80, descrita em [KANBAN_ENCERRAMENTO.md](KANBAN_ENCERRAMENTO.md).

As notificações e as respostas dos convites continuam no banco. Ao entrar, o frontend carrega os pendentes. Cada conexão envia `sincronizar` depois de ser registrada, e o frontend busca novamente os dados para cobrir alterações entre a carga inicial e a conexão. Novos eventos invalidam somente os caches do usuário conectado; lotes são agrupados para evitar consultas duplicadas.

Os listeners de persistência cobrem criação, alteração e remoção de notificações, participantes e reuniões. A emissão só é agendada após o commit. A leitura em lote das notificações, que usa uma atualização SQL direta, também emite um evento. Uma transação desfeita não dispara push. O processamento de avisos roda em scheduler próprio, sem executar consultas de pendências por aba; só consulta participantes quando uma reunião muda e verifica as sessões dos clientes conectados.

## Conexão e autenticação

- Um canal por aba, compartilhado entre o sininho, os avisos pequenos e as atualizações da agenda.
- Ao ocultar a aba ou ficar offline, a conexão é cancelada. Ao retornar, ela é aberta novamente e sincroniza os avisos salvos.
- Falhas transitórias e encerramentos do servidor usam reconexão com espera progressiva até aproximadamente 30 segundos.
- Os interceptadores existentes renovam o JWT quando necessário. A conexão respeita a expiração do token, tem duração máxima de dez minutos e verifica revogação de sessão antes dos eventos e nas mensagens de manutenção.
- Mensagens `heartbeat` a cada vinte segundos mantêm a conexão ativa. Não consultam as listas de notificações nem de convites.
- Logout, troca de usuário e desmontagem cancelam o stream e as atualizações agendadas.

Não há Web Push, service worker ou solicitação de permissão do navegador. Avisos gerados com o sistema fechado aparecem no próximo acesso.

## Popup de notificações

As notificações não lidas também aparecem em um popup global para o usuário autenticado, em qualquer tela. A fila apresenta um aviso por vez e aguarda enquanto outro diálogo estiver aberto ou a aba estiver oculta. Os avisos pendentes são conferidos no servidor antes da apresentação inicial, e as novas notificações chegam pelo canal SSE existente, compartilhando a consulta do sininho sem criar polling adicional.

Por padrão, os avisos usam o mesmo toast das boas-vindas, no canto da tela, sem bloquear a navegação. O fechamento automático após oito segundos ou o botão de fechar dispensam somente esse popup; o aviso permanece não lido no sininho. **Ver detalhes** marca como lido e abre o destino permitido. Avisos sem link oferecem **Marcar como lida**. Se a operação falhar, o aviso permite tentar novamente.

Somente `TAREFA_VENCIMENTO` e `TAREFA_ATRASADA` abrem um popup grande, sem fechamento automático. Essas notificações também ficam no sininho. **Agora não**, o botão de fechar e Escape dispensam a janela sem marcar como lida; **Ver tarefa** confirma a leitura e abre a tarefa no serviço correspondente. A fila aguarda outros diálogos e mantém somente um popup por vez.

As demais notificações — atribuições, participação na equipe, propostas, contratos, vencimento de contrato, reuniões e avisos gerais — usam o popup pequeno e o sininho. Tipos novos ou ainda desconhecidos também usam o popup pequeno.

## Prazos e atrasos de tarefas

O processamento horário mantém os marcos de três dias antes, um dia antes, no vencimento e no primeiro atraso. Se o sistema estiver indisponível no primeiro dia de atraso, recupera esse aviso no próximo processamento. Cada marco é enviado uma vez por tarefa, prazo e destinatário; mudar o prazo permite novos avisos.

Nas tarefas do contrato, os destinatários internos são todos os responsáveis da tarefa e o responsável técnico do serviço da coluna, definido pelo comercial. Não há duplicação quando a mesma pessoa ocupa ambos os papéis. Os responsáveis de outros serviços e o autor da atribuição não entram automaticamente. Contratos antigos continuam usando o responsável técnico anterior quando ainda não têm distribuição por serviço. Nas tarefas comerciais, os responsáveis da tarefa recebem o aviso interno.

Os avisos são persistidos mesmo que o usuário esteja offline ou que o envio de e-mail falhe. A deduplicação interna usa chaves com prefixo `APP:` na tabela de controle de prazos; os e-mails mantêm suas próprias chaves e destinatários. A gravação do aviso e do controle ocorre na mesma transação e os sinais SSE são enviados após o commit. Não são necessárias novas variáveis de ambiente nem migração de banco.

Os IDs dos popups dispensados são mantidos no `sessionStorage`, separados por usuário, para evitar repetições ao navegar, recarregar ou reconectar durante a sessão da aba. Se o armazenamento estiver bloqueado, a deduplicação permanece em memória. Logout e troca de conta removem o popup e impedem que ações pendentes abram avisos da sessão anterior.

Convites de reunião aparecem somente no aviso pequeno e no sininho; não abrem outro diálogo automaticamente. O convidado pode abrir os detalhes na Agenda e aceitar a reunião. Os sinais de convite atualizam a lista da agenda para que esse acesso funcione mesmo se ela já estiver aberta.

O organizador continua participante da reunião para consultá-la e gerenciá-la, mas é excluído dos destinatários de `REUNIAO_AGENDADA`. A regra vale na criação, nas alterações e no envio adiado enquanto o Google Meet é gerado. As notificações anteriores já gravadas permanecem no histórico.

## Publicação

Publique primeiro o backend e depois o frontend, que remove os intervalos de cinco segundos dos convites e de sessenta segundos do sininho. Esta funcionalidade não exige novas variáveis de ambiente nem migração de banco.

O proxy deve permitir streaming de `text/event-stream` sem buffering ou cache. A resposta define `Cache-Control: no-cache, no-store, no-transform` e `X-Accel-Buffering: no`. No DevTools, é esperado um GET de longa duração com status 200; os eventos chegam pela mesma resposta. Verifique também a recuperação após um deploy, uma queda de rede e a volta à aba.

**Escala:** a distribuição dos sinais SSE é local à instância da API. Esta implementação atende uma instância do backend. Antes de habilitar várias réplicas ou separar processos que gravam avisos, adicione distribuição de eventos entre instâncias, por exemplo Redis Pub/Sub com ressincronização pelo banco. O histórico persistido não depende do stream.

Referências: [SSE no Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#webmvc-mvc-ann-async-sse), [eventos após commit](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html), [cliente SSE](https://github.com/Azure/fetch-event-source).

# Avisos em tempo real

O frontend abre `GET /avisos/stream` com o mesmo `Authorization: Bearer` das APIs. O canal SSE sinaliza mudanças em notificações e convites do usuário autenticado; não recebe um ID de usuário como parâmetro e não transporta conteúdo dos avisos.

O sinal `kanban` sincroniza a lista de quadros e a permissão do menu ao atribuir tarefas ou encerrar serviços. No encerramento, todas as abas conectadas recebem somente o sinal, sem IDs ou conteúdo do contrato; cada aba consulta novamente os dados permitidos ao seu usuário. `sincronizar` também reconcilia esses caches após reconexões. O encerramento por serviço exige a migração V80, descrita em [KANBAN_ENCERRAMENTO.md](KANBAN_ENCERRAMENTO.md).

As notificações e as respostas dos convites continuam no banco. Ao entrar, o frontend carrega os pendentes. Cada conexão envia `sincronizar` depois de ser registrada, e o frontend busca novamente os dados para cobrir alterações entre a carga inicial e a conexão. Novos eventos invalidam somente os caches do usuário conectado; lotes são agrupados para evitar consultas duplicadas.

Os listeners de persistência cobrem criação, alteração e remoção de notificações, participantes e reuniões. A emissão só é agendada após o commit. A leitura em lote das notificações, que usa uma atualização SQL direta, também emite um evento. Uma transação desfeita não dispara push. O processamento de avisos roda em scheduler próprio, sem executar consultas de pendências por aba; só consulta participantes quando uma reunião muda e verifica as sessões dos clientes conectados.

## Conexão e autenticação

- Um canal por aba, compartilhado entre o sininho e os popups de reuniões.
- Ao ocultar a aba ou ficar offline, a conexão é cancelada. Ao retornar, ela é aberta novamente e sincroniza os avisos salvos.
- Falhas transitórias e encerramentos do servidor usam reconexão com espera progressiva até aproximadamente 30 segundos.
- Os interceptadores existentes renovam o JWT quando necessário. A conexão respeita a expiração do token, tem duração máxima de dez minutos e verifica revogação de sessão antes dos eventos e nas mensagens de manutenção.
- Mensagens `heartbeat` a cada vinte segundos mantêm a conexão ativa. Não consultam as listas de notificações nem de convites.
- Logout, troca de usuário e desmontagem cancelam o stream e as atualizações agendadas.

Não há Web Push, service worker ou solicitação de permissão do navegador. Avisos gerados com o sistema fechado aparecem no próximo acesso.

## Popup de notificações

As notificações não lidas também aparecem em um popup global para o usuário autenticado, em qualquer tela. A fila apresenta um aviso por vez e aguarda enquanto outro diálogo estiver aberto ou a aba estiver oculta. Os avisos pendentes são conferidos no servidor antes da apresentação inicial, e as novas notificações chegam pelo canal SSE existente, compartilhando a consulta do sininho sem criar polling adicional.

**Agora não**, o botão de fechar e Escape dispensam somente o popup; o aviso permanece não lido no sininho. **Ver detalhes** marca como lido e abre o destino permitido. Avisos sem link oferecem **Marcar como lida**. Se a operação falhar, o popup permite tentar novamente.

Os IDs dos popups dispensados são mantidos no `sessionStorage`, separados por usuário, para evitar repetições ao navegar, recarregar ou reconectar durante a sessão da aba. Se o armazenamento estiver bloqueado, a deduplicação permanece em memória. Logout e troca de conta removem o popup e impedem que ações pendentes abram avisos da sessão anterior. O popup específico de convites de reunião continua disponível.

## Publicação

Publique primeiro o backend e depois o frontend, que remove os intervalos de cinco segundos dos convites e de sessenta segundos do sininho. Esta funcionalidade não exige novas variáveis de ambiente nem migração de banco.

O proxy deve permitir streaming de `text/event-stream` sem buffering ou cache. A resposta define `Cache-Control: no-cache, no-store, no-transform` e `X-Accel-Buffering: no`. No DevTools, é esperado um GET de longa duração com status 200; os eventos chegam pela mesma resposta. Verifique também a recuperação após um deploy, uma queda de rede e a volta à aba.

**Escala:** a distribuição dos sinais SSE é local à instância da API. Esta implementação atende uma instância do backend. Antes de habilitar várias réplicas ou separar processos que gravam avisos, adicione distribuição de eventos entre instâncias, por exemplo Redis Pub/Sub com ressincronização pelo banco. O histórico persistido não depende do stream.

Referências: [SSE no Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#webmvc-mvc-ann-async-sse), [eventos após commit](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html), [cliente SSE](https://github.com/Azure/fetch-event-source).

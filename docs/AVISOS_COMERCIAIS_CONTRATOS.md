# Avisos de propostas e contratos no sistema

| Evento | Destinatários |
| --- | --- |
| Proposta aceita | Comercial responsável pela negociação; na ausência de negociação vinculada, responsável pela proposta |
| Cliente solicita revisão ou reprova uma proposta | Mesmo comercial responsável pela proposta/negociação |
| Novo contrato, inclusive a preparação criada ao marcar uma venda como ganha | Comercial responsável e usuários ativos com cargo do Jurídico |
| Contrato aprovado internamente ou após assinatura confirmada pela ZapSign | Comercial, Jurídico e todos os responsáveis técnicos dos serviços |
| Cliente solicita revisão ou reprova um contrato | Comercial responsável e usuários ativos com cargo do Jurídico |

O Jurídico é identificado pelo nome do cargo contendo `Jurídic` ou `Juridic`, sem diferenciar maiúsculas de minúsculas. O usuário e o cargo devem estar ativos. Ter apenas permissões de contratos não inclui uma pessoa nessa distribuição. Os demais destinatários também precisam estar ativos.

Cada pessoa recebe um aviso por evento mesmo quando acumula vários papéis ou é responsável por vários serviços. Repetir uma aprovação ou a confirmação de assinatura não duplica o aviso; uma nova versão aprovada gera um novo aviso. As notificações são gravadas na mesma transação da alteração, permanecem pendentes para quem está offline e atualizam o sininho via SSE após o commit.

Os avisos incluem links para a proposta, o contrato ou o Kanban do serviço do responsável técnico. A aprovação também sincroniza o acesso ao Kanban. Os fluxos de e-mail existentes continuam independentes destes avisos internos.

Nas solicitações de revisão e reprovações, o aviso identifica a versão e inclui o comentário geral ou a justificativa do cliente, quando informado. Os destinatários recebem o aviso no sininho e no popup global. Preparar ou reenviar uma versão que ainda aguarda resposta não dispara esses avisos; uma nova versão pode receber uma nova resposta e gerar sua própria notificação.

Esta alteração não exige novas variáveis de ambiente nem migração de banco. Publique o backend antes do frontend, que passa a reconhecer os links de propostas e contratos no sininho.

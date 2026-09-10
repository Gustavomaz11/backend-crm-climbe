# Pré-vendas, campanhas e cadências

Implementação no backend `back/climb-services` e no frontend `front/climb-app`.

## Acesso e operação

1. Abra **Pré-vendas** no menu comercial (`/pipeline-pre-vendas`). O funil tem cinco etapas fixas: Lista de leads, Tentativa de contato, Lead conectado, Reunião marcada e Reunião realizada.
2. Em **Pessoas**, selecione os contatos e use **Criar leads**. Escolha responsável, etapa, origem e, opcionalmente, campanha. Para uma pessoa com várias empresas, escolha a empresa desta abordagem. O lote aceita até 100 pessoas e é gravado em uma transação.
3. Uma pessoa pode originar vários leads. Cada lead pode ter uma campanha de origem; depois de associada, ela é preservada. Para outra campanha, crie outro lead. Telefone e e-mail podem ficar vazios.
4. Em **Campanhas**, configure participantes, dias de execução e a sequência de cada etapa. É possível cadastrar a campanha antes de adicionar leads. Uma etapa sem sequência não gera tarefas.
5. Ao entrar em uma etapa configurada de uma campanha ativa, o lead recebe a primeira tarefa. A tarefa mostra o contato, orientação e script completo, com opção de copiar o conteúdo.
6. **Cancelar tarefa** exige um motivo: sem canal de contato, desnecessária ou repetida. O comentário é opcional. O cancelamento libera o próximo passo da cadência e mantém a tarefa como cancelada, sem contá-la como concluída.
7. Ao sair da etapa ou encerrar o lead, a tarefa automática pendente é cancelada com motivo de sistema. O retorno à etapa não reinicia a sequência. Use **Reiniciar cadência desta etapa** quando houver uma nova abordagem; essa ação exige permissão de execução de campanhas.
8. **Marcar ganho** permite escolher funil e responsável da venda. A venda entra em Diagnóstico e precisa atender aos campos obrigatórios dessa etapa. Pessoa, empresa, contatos, campanha, estratégia, tags e campos aplicáveis são copiados. Os registros oferecem links entre a pré-venda e a venda. O histórico original permanece na pré-venda.
9. Ganho e perda não mudam a coluna da pré-venda. Use o filtro de situação para consultar encerrados. Para editar ou movimentar um lead encerrado, reative-o. Ganhar novamente o mesmo lead mantém a venda já gerada.
10. Em **Tags e campos**, configure tags com cores e campos de texto, número, data, lista de opções ou sim/não. O escopo pode ser pré-vendas, vendas ou ambos. Inativar um cadastro preserva os valores já usados; tipo, escopo e opções de um campo existente não mudam.

O dashboard comercial considera apenas vendas quando nenhum funil é selecionado. Pré-vendas pode ser selecionado explicitamente. O relatório de cancelamentos tem filtros próprios de período, campanha e responsável pela tarefa, com os cancelamentos automáticos identificados separadamente.

## Compatibilidade e regras

- A migração V54 cria o funil fixo e as novas tabelas/colunas. As nove etapas originais de vendas, inclusive as antigas etapas de reunião, permanecem disponíveis para os registros existentes. A criação padrão de vendas e a conversão de pré-venda priorizam Diagnóstico.
- Não há classificação automática dos negócios existentes como pré-venda, nem escolha automática de uma campanha principal para negócios com várias campanhas antigas.
- As cadências gerais antigas continuam disponíveis. Planos editados criam uma nova versão; execuções já iniciadas continuam com a versão anterior, inclusive se o fluxo tiver sido removido da configuração atual. O conteúdo do script é preservado em cada versão do plano.
- A migração preserva as referências entre tarefas antigas, campanhas, passos e scripts. Para scripts antigos, a versão inicial captura o conteúdo disponível no momento da migração.
- Atividades automáticas encerradas não podem ser reabertas individualmente, pois a cadência já avançou. É possível criar uma atividade manual ou reiniciar explicitamente o fluxo encerrado.
- Conversão e encerramento da pré-venda ocorrem na mesma transação. Uma restrição única no banco e o bloqueio do lead impedem gerar duas vendas para a mesma origem.
- Os campos exclusivos de pré-vendas não são copiados para a venda. Propostas e contratos continuam vinculados ao fluxo de vendas.
- O cancelamento registra motivo, comentário, data e usuário. Registros cancelados antes desta implementação podem aparecer sem motivo/data; datas ausentes não são incluídas quando há filtro de período.

## Permissões

As ações usam as permissões comerciais existentes: visualizar, criar, editar, movimentar, concluir, atividades e campanhas. Cancelar tarefas usa `COMERCIAL_TAREFA_CONCLUIR`. Ganhar pré-vendas usa `COMERCIAL_CONCLUIR`; reiniciar cadência usa `COMERCIAL_CAMPANHA_EXECUTAR`.

Foi adicionada `COMERCIAL_CADASTROS_GERENCIAR`. A V54 a concede aos usuários que já possuem `COMERCIAL_FUNIL_EDITAR`. Os demais acessos podem ser configurados na administração de permissões.

## Atualização do ambiente

A V54 é executada pelo Flyway no início do backend, conforme a configuração do ambiente. O backend e o frontend desta entrega devem ser atualizados juntos. Use Java 25 para compilar o backend.

Nesta implementação, a migração foi executada somente em um MySQL temporário de teste. O banco indicado pelo `.env` não foi acessado ou migrado, e não foi realizado deploy.

## Validação

- Testes unitários e integrados do backend com perfil `test` e H2, cobrindo conversão idempotente, vínculos, campos obrigatórios, permissões, campos personalizados, lote de 30 leads, cancelamento, retorno à etapa e versões da cadência.
- Migração real via Flyway de V1 até V53 e depois V54, em MySQL 8.4 isolado, com campanha, tarefa, script e execução de exemplo criados antes da V54. Oito verificações confirmaram o novo funil e a preservação dos registros e vínculos antigos.
- Testes do frontend, verificação de tipos com os dois projetos TypeScript, ESLint nos arquivos alterados e geração de produção com Vite.
- Um teste existente de revisão documental foi ajustado para aguardar o carregamento da página antes de clicar nas marcações.

Comandos de verificação:

```powershell
# Em back/climb-services, com JAVA_HOME apontando para Java 25
./mvnw.cmd test

# Em front/climb-app
npm run test
npx tsc -p tsconfig.app.json --noEmit
npx tsc -p tsconfig.node.json --noEmit
npm run build
```

A suíte completa do backend foi executada em uma cópia temporária do código, pois a limpeza de `target` encontrou arquivos bloqueados na pasta de trabalho sincronizada. Essa cópia não incluiu `.env`.

## Roteiro de homologação com 30 leads

Use uma base de homologação e uma campanha de teste. O teste automatizado de lote usa dados fictícios; a validação operacional abaixo ainda deve ser realizada pela equipe.

1. Separe 30 contatos, incluindo pessoas sem telefone, sem e-mail e com várias empresas. Crie leads em Lista de leads e confira os vínculos escolhidos.
2. Mova grupos para Tentativa de contato e Lead conectado. Confira que somente etapas com fluxo configurado criam tarefas.
3. Conclua e cancele tarefas com cada motivo. Confira o próximo passo e o relatório, inclusive seus filtros.
4. Tire um lead da etapa com tarefa pendente, retorne e confira que não surgiram tarefas repetidas. Solicite o reinício e confira a nova tarefa.
5. Pause a campanha, altere uma sequência e reative. Compare uma execução antiga com um lead novo para confirmar a preservação do plano anterior.
6. Ganhe alguns leads, escolhendo o responsável da venda. Confira Diagnóstico, dados copiados, campos por escopo e os links de histórico. Repita a ação para confirmar a venda única.
7. Perca outros leads e consulte-os pelo filtro. Confira que os ganhos de pré-vendas não entram como vendas ganhas no dashboard padrão.
8. Confira propostas, contratos e revisões de um negócio de vendas já existente. Os testes automatizados não substituem essa homologação visual e operacional.

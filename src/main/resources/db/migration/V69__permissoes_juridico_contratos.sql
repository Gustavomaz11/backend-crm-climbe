-- Permissões independentes do Jurídico; os vínculos existentes são preservados.
INSERT INTO permissoes (codigo, descricao)
SELECT nova.codigo, nova.descricao
FROM (
    SELECT 'JURIDICO_CONTRATO_LER' AS codigo, 'Ler contratos e consultar arquivos, proposta vinculada, revisões e histórico' AS descricao
    UNION ALL SELECT 'JURIDICO_CONTRATO_CRIAR', 'Criar contratos'
    UNION ALL SELECT 'JURIDICO_CONTRATO_EDITAR', 'Editar dados do contrato'
    UNION ALL SELECT 'JURIDICO_CONTRATO_DELETAR', 'Excluir contratos'
    UNION ALL SELECT 'JURIDICO_CONTRATO_MOVIMENTAR', 'Movimentar contratos entre colunas'
    UNION ALL SELECT 'JURIDICO_CONTRATO_ANEXAR_ARQUIVO', 'Anexar ou substituir o arquivo do contrato'
    UNION ALL SELECT 'JURIDICO_CONTRATO_ENVIAR_CLIENTE', 'Anexar e enviar o contrato ao cliente'
    UNION ALL SELECT 'JURIDICO_CONTRATO_ENVIAR_NOVA_VERSAO', 'Enviar uma nova versão do contrato ao cliente'
    UNION ALL SELECT 'JURIDICO_CONTRATO_REENVIAR', 'Reenviar o e-mail do contrato ao cliente'
    UNION ALL SELECT 'JURIDICO_CONTRATO_RESPONSAVEIS_EDITAR', 'Alterar responsável técnico e participantes do contrato'
    UNION ALL SELECT 'JURIDICO_CONTRATO_VENCIMENTOS_EDITAR', 'Alterar vencimentos das parcelas do contrato'
    UNION ALL SELECT 'JURIDICO_CONTRATO_APROVAR', 'Aprovar ou rejeitar contratos internamente'
    UNION ALL SELECT 'JURIDICO_CONTRATO_DESVINCULAR_PROPOSTA', 'Desvincular a proposta do contrato'
    UNION ALL SELECT 'JURIDICO_CONTRATO_ARQUIVAR', 'Arquivar contratos concluídos'
    UNION ALL SELECT 'JURIDICO_CONTRATO_DESARQUIVAR', 'Desarquivar contratos'
) nova
WHERE NOT EXISTS (SELECT 1 FROM permissoes existente WHERE existente.codigo = nova.codigo);

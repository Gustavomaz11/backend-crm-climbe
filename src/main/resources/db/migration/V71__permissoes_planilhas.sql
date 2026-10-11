-- A permissão legada continua limitada a leitura/edição; novas ações são independentes.
INSERT INTO permissoes (codigo, descricao)
SELECT nova.codigo, nova.descricao FROM (
    SELECT 'PLANILHA_LER' AS codigo, 'Ler planilhas dos contratos aos quais tem acesso' AS descricao
    UNION ALL SELECT 'PLANILHA_CRIAR', 'Criar planilhas em contratos aos quais tem acesso'
    UNION ALL SELECT 'PLANILHA_EDITAR', 'Editar planilhas desbloqueadas em contratos aos quais tem acesso'
    UNION ALL SELECT 'PLANILHA_DELETAR', 'Excluir planilhas em contratos aos quais tem acesso'
    UNION ALL SELECT 'PLANILHA_CONFIGURAR_ACESSO', 'Configurar bloqueio e regras de acesso das planilhas'
) nova WHERE NOT EXISTS (SELECT 1 FROM permissoes p WHERE p.codigo = nova.codigo);

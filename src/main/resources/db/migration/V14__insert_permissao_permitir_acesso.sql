INSERT INTO permissoes (descricao, codigo)
SELECT 'Permite aprovar ou rejeitar solicitações de acesso de usuários', 'PERMITIR_ACESSO'
WHERE NOT EXISTS (SELECT 1 FROM permissoes WHERE codigo = 'PERMITIR_ACESSO');

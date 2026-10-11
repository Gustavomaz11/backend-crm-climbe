-- Substitui os acessos completos por quatro ações independentes.
-- Preserva os acessos dos usuários, dos grupos e das aprovações Google pendentes.
CREATE TABLE permissao_crud_migracao (
    codigo_antigo VARCHAR(100) PRIMARY KEY,
    recurso VARCHAR(100) NOT NULL,
    nome VARCHAR(100) NOT NULL
);

INSERT INTO permissao_crud_migracao (codigo_antigo, recurso, nome) VALUES
('CONTRATO_CRUD', 'CONTRATO', 'contratos'),
('CARGO_CRUD', 'CARGO', 'cargos'),
('DOCUMENTO_JURIDICO_CRUD', 'DOCUMENTO_JURIDICO', 'documentos jurídicos'),
('RELATORIO_CRUD', 'RELATORIO', 'relatórios'),
('PROPOSTA_CRUD', 'PROPOSTA', 'propostas');

INSERT INTO permissoes (codigo, descricao)
SELECT CONCAT(m.recurso, '_', a.codigo), CONCAT(a.nome, ' ', m.nome)
FROM permissao_crud_migracao m
CROSS JOIN (
    SELECT 'LER' AS codigo, 'Ler' AS nome
    UNION ALL SELECT 'CRIAR', 'Criar'
    UNION ALL SELECT 'EDITAR', 'Editar'
    UNION ALL SELECT 'DELETAR', 'Deletar'
) a
LEFT JOIN permissoes existente ON existente.codigo = CONCAT(m.recurso, '_', a.codigo)
WHERE existente.id_permissao IS NULL;

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT DISTINCT u.id_usuario, nova.id_permissao
FROM usuario_permissoes u
JOIN permissoes antiga ON antiga.id_permissao = u.id_permissao
JOIN permissao_crud_migracao m ON m.codigo_antigo = antiga.codigo
JOIN permissoes nova ON nova.codigo IN (
    CONCAT(m.recurso, '_LER'), CONCAT(m.recurso, '_CRIAR'),
    CONCAT(m.recurso, '_EDITAR'), CONCAT(m.recurso, '_DELETAR')
)
LEFT JOIN usuario_permissoes existente
    ON existente.id_usuario = u.id_usuario AND existente.id_permissao = nova.id_permissao
WHERE existente.id IS NULL;

INSERT INTO grupo_permissao_itens (grupo_id, permissao_id)
SELECT DISTINCT g.grupo_id, nova.id_permissao
FROM grupo_permissao_itens g
JOIN permissoes antiga ON antiga.id_permissao = g.permissao_id
JOIN permissao_crud_migracao m ON m.codigo_antigo = antiga.codigo
JOIN permissoes nova ON nova.codigo IN (
    CONCAT(m.recurso, '_LER'), CONCAT(m.recurso, '_CRIAR'),
    CONCAT(m.recurso, '_EDITAR'), CONCAT(m.recurso, '_DELETAR')
)
LEFT JOIN grupo_permissao_itens existente
    ON existente.grupo_id = g.grupo_id AND existente.permissao_id = nova.id_permissao
WHERE existente.permissao_id IS NULL;

INSERT INTO oauth2_pending_permissoes (pending_id, id_permissao)
SELECT DISTINCT o.pending_id, nova.id_permissao
FROM oauth2_pending_permissoes o
JOIN permissoes antiga ON antiga.id_permissao = o.id_permissao
JOIN permissao_crud_migracao m ON m.codigo_antigo = antiga.codigo
JOIN permissoes nova ON nova.codigo IN (
    CONCAT(m.recurso, '_LER'), CONCAT(m.recurso, '_CRIAR'),
    CONCAT(m.recurso, '_EDITAR'), CONCAT(m.recurso, '_DELETAR')
)
LEFT JOIN oauth2_pending_permissoes existente
    ON existente.pending_id = o.pending_id AND existente.id_permissao = nova.id_permissao
WHERE existente.id_permissao IS NULL;

DELETE FROM usuario_permissoes WHERE id_permissao IN (
    SELECT p.id_permissao FROM permissoes p JOIN permissao_crud_migracao m ON m.codigo_antigo = p.codigo
);
DELETE FROM grupo_permissao_itens WHERE permissao_id IN (
    SELECT p.id_permissao FROM permissoes p JOIN permissao_crud_migracao m ON m.codigo_antigo = p.codigo
);
DELETE FROM oauth2_pending_permissoes WHERE id_permissao IN (
    SELECT p.id_permissao FROM permissoes p JOIN permissao_crud_migracao m ON m.codigo_antigo = p.codigo
);
DELETE FROM permissoes WHERE codigo IN (SELECT codigo_antigo FROM permissao_crud_migracao);
DROP TABLE permissao_crud_migracao;

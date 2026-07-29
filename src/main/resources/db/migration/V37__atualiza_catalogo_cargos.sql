ALTER TABLE cargos
    ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;

-- Normaliza nomes equivalentes sem perder os IDs já vinculados a usuários.
UPDATE cargos SET nome = TRIM(nome);
UPDATE cargos SET nome = 'CEO - Chief Executive Officer' WHERE nome = 'CEO';
UPDATE cargos SET nome = 'CMO - Chief Marketing Officer' WHERE nome = 'CMO';
UPDATE cargos SET nome = 'Analista Sênior' WHERE nome = 'Analista Senior';
UPDATE cargos SET nome = 'Analista Júnior' WHERE nome = 'Analista Junior';
UPDATE cargos SET nome = 'Head de Contabilidade' WHERE nome = 'Head de Contabilide';

-- Insere somente cargos ainda inexistentes, permitindo aplicar a migration em bases já usadas.
INSERT INTO cargos (nome, ativo)
SELECT catalogo.nome, TRUE
FROM (
    SELECT 'Membro do Conselho' AS nome
    UNION ALL SELECT 'CEO - Chief Executive Officer'
    UNION ALL SELECT 'CMO - Chief Marketing Officer'
    UNION ALL SELECT 'Diretor de Compliance'
    UNION ALL SELECT 'Analista Sênior'
    UNION ALL SELECT 'Analista Pleno'
    UNION ALL SELECT 'Analista Júnior'
    UNION ALL SELECT 'Analista Trainee'
    UNION ALL SELECT 'Analista de BPO'
    UNION ALL SELECT 'Contador'
    UNION ALL SELECT 'Diretor Jurídico'
    UNION ALL SELECT 'Analista Chefe'
    UNION ALL SELECT 'Head de Contabilidade'
    UNION ALL SELECT 'Head de TI'
    UNION ALL SELECT 'Analista de TI'
    UNION ALL SELECT 'Analista Comercial'
    UNION ALL SELECT 'Diretor de Marketing'
    UNION ALL SELECT 'Analista de Marketing'
) catalogo
WHERE NOT EXISTS (
    SELECT 1
    FROM cargos existente
    WHERE existente.nome = catalogo.nome
);

-- Mantém registros antigos para preservar o histórico, mas fora das novas seleções.
UPDATE cargos SET ativo = FALSE;

UPDATE cargos cargo
JOIN (
    SELECT nome, MIN(id) AS id
    FROM cargos
    WHERE nome IN (
        'Membro do Conselho',
        'CEO - Chief Executive Officer',
        'CMO - Chief Marketing Officer',
        'Diretor de Compliance',
        'Analista Sênior',
        'Analista Pleno',
        'Analista Júnior',
        'Analista Trainee',
        'Analista de BPO',
        'Contador',
        'Diretor Jurídico',
        'Analista Chefe',
        'Head de Contabilidade',
        'Head de TI',
        'Analista de TI',
        'Analista Comercial',
        'Diretor de Marketing',
        'Analista de Marketing'
    )
    GROUP BY nome
) catalogo_ativo ON catalogo_ativo.id = cargo.id
SET cargo.ativo = TRUE;

INSERT INTO permissoes (descricao, codigo)
SELECT 'Visualizar todas as tarefas no kanban comercial',
       'COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS'
WHERE NOT EXISTS (
    SELECT 1
    FROM permissoes
    WHERE codigo = 'COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS'
);

INSERT INTO permissoes (descricao, codigo)
SELECT 'Visualizar todas as tarefas no kanban geral',
       'CONTRATO_KANBAN_VISUALIZAR_TODAS_TAREFAS'
WHERE NOT EXISTS (
    SELECT 1
    FROM permissoes
    WHERE codigo = 'CONTRATO_KANBAN_VISUALIZAR_TODAS_TAREFAS'
);

-- Mantem a conta administrativa com acesso integral apos a inclusao de novas permissoes.
INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
JOIN permissoes permissao ON permissao.codigo IN (
    'COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS',
    'CONTRATO_KANBAN_VISUALIZAR_TODAS_TAREFAS'
)
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'administrador@climbe.com'
  AND usuario_permissao.id IS NULL;

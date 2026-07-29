-- O usuário padrão de desenvolvimento precisa acessar todas as áreas do sistema.
-- O LEFT JOIN mantém a migration idempotente caso alguma permissão já esteja associada.
INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
CROSS JOIN permissoes permissao
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'teste@climb.com'
  AND usuario_permissao.id IS NULL;

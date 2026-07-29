-- Promove a conta padrao a administrador e mantem a senha apenas como hash BCrypt.
UPDATE usuarios
SET nome_completo = 'Administrador',
    email = 'administrador@climbe.com',
    senha_hash = '$2b$10$buGQ1IwnsN5ZrrDJm80t4ueFDfHKXZPtwiu/4sn53SpKfsMsI2beW',
    situacao = 'ATIVO'
WHERE email = 'teste@climbe.com';

-- Concede todas as permissoes existentes sem duplicar associacoes anteriores.
INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
CROSS JOIN permissoes permissao
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'administrador@climbe.com'
  AND usuario_permissao.id IS NULL;

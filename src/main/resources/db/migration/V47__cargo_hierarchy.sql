ALTER TABLE cargos
    ADD COLUMN cargo_superior_id BIGINT NULL,
    ADD COLUMN ordem_hierarquia INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_cargo_hierarquia_superior
        FOREIGN KEY (cargo_superior_id) REFERENCES cargos(id),
    ADD INDEX idx_cargo_hierarquia_superior_ordem (cargo_superior_id, ordem_hierarquia);

-- A raiz "Administrador" é virtual. Inicialmente, todos os cargos ativos ficam logo abaixo dela.
UPDATE cargos
SET cargo_superior_id = NULL,
    ordem_hierarquia = id;

INSERT INTO permissoes (descricao, codigo)
SELECT 'Permitir edição de hierarquia de cargos',
       'CARGO_HIERARQUIA_EDITAR'
WHERE NOT EXISTS (
    SELECT 1
    FROM permissoes
    WHERE codigo = 'CARGO_HIERARQUIA_EDITAR'
);

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
JOIN permissoes permissao ON permissao.codigo = 'CARGO_HIERARQUIA_EDITAR'
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'administrador@climbe.com'
  AND usuario_permissao.id IS NULL;

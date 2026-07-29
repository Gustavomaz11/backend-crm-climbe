CREATE TABLE pipeline_vendas_funis (
    id_funil BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(80) NOT NULL UNIQUE,
    nome VARCHAR(150) NOT NULL,
    descricao TEXT NULL,
    estrategia VARCHAR(180) NOT NULL,
    posicao INT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pipeline_funil_posicao (posicao),
    INDEX idx_pipeline_funil_ativo (ativo)
);

INSERT INTO pipeline_vendas_funis (codigo, nome, descricao, estrategia, posicao, ativo)
VALUES (
    'PIPELINE_COMERCIAL_PADRAO',
    'Pipeline Comercial Padrão',
    'Funil inicial migrado da configuração comercial existente.',
    'Comercial geral',
    1,
    TRUE
);

ALTER TABLE pipeline_vendas_etapas
    ADD COLUMN funil_id BIGINT NULL AFTER id_etapa,
    ADD COLUMN objetivo TEXT NULL AFTER nome,
    ADD COLUMN criterios_conclusao TEXT NULL AFTER objetivo,
    ADD COLUMN tempo_maximo_permanencia_dias INT NULL AFTER criterios_conclusao,
    ADD COLUMN campos_obrigatorios JSON NULL AFTER tempo_maximo_permanencia_dias;

UPDATE pipeline_vendas_etapas
SET funil_id = (SELECT id_funil FROM pipeline_vendas_funis WHERE codigo = 'PIPELINE_COMERCIAL_PADRAO'),
    campos_obrigatorios = JSON_ARRAY();

ALTER TABLE pipeline_vendas_etapas
    MODIFY COLUMN funil_id BIGINT NOT NULL,
    DROP INDEX codigo,
    ADD CONSTRAINT fk_pipeline_etapa_funil
        FOREIGN KEY (funil_id) REFERENCES pipeline_vendas_funis(id_funil),
    ADD CONSTRAINT uk_pipeline_etapa_funil_codigo UNIQUE (funil_id, codigo),
    ADD INDEX idx_pipeline_etapa_funil_posicao (funil_id, posicao);

ALTER TABLE pipeline_vendas_negocios
    ADD COLUMN funil_id BIGINT NULL AFTER id_negocio;

UPDATE pipeline_vendas_negocios negocio
JOIN pipeline_vendas_etapas etapa ON etapa.id_etapa = negocio.etapa_id
SET negocio.funil_id = etapa.funil_id;

ALTER TABLE pipeline_vendas_negocios
    MODIFY COLUMN funil_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_pipeline_negocio_funil
        FOREIGN KEY (funil_id) REFERENCES pipeline_vendas_funis(id_funil),
    ADD INDEX idx_pipeline_negocio_funil (funil_id);

INSERT INTO permissoes (descricao, codigo) VALUES
    ('Comercial — visualizar configuração de funis', 'COMERCIAL_FUNIL_VISUALIZAR'),
    ('Comercial — criar funis', 'COMERCIAL_FUNIL_CRIAR'),
    ('Comercial — editar funis e etapas', 'COMERCIAL_FUNIL_EDITAR'),
    ('Comercial — duplicar funis', 'COMERCIAL_FUNIL_DUPLICAR'),
    ('Comercial — alterar a ordem dos funis', 'COMERCIAL_FUNIL_ORDENAR');

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
JOIN permissoes permissao ON permissao.codigo IN (
    'COMERCIAL_FUNIL_VISUALIZAR',
    'COMERCIAL_FUNIL_CRIAR',
    'COMERCIAL_FUNIL_EDITAR',
    'COMERCIAL_FUNIL_DUPLICAR',
    'COMERCIAL_FUNIL_ORDENAR'
)
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'teste@climb.com'
  AND usuario_permissao.id IS NULL;

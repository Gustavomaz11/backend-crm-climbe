CREATE TABLE grupos_cargos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_grupos_cargos_nome UNIQUE (nome)
);

ALTER TABLE cargos
    ADD COLUMN grupo_id BIGINT NULL,
    ADD CONSTRAINT fk_cargos_grupo
        FOREIGN KEY (grupo_id) REFERENCES grupos_cargos(id),
    ADD INDEX idx_cargos_grupo_ativo (grupo_id, ativo);

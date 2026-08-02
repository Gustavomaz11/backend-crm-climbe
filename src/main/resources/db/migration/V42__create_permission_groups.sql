CREATE TABLE grupos_permissoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_grupos_permissoes_nome UNIQUE (nome)
);

CREATE TABLE grupo_permissao_itens (
    grupo_id BIGINT NOT NULL,
    permissao_id BIGINT NOT NULL,
    PRIMARY KEY (grupo_id, permissao_id),
    CONSTRAINT fk_grupo_permissao_grupo
        FOREIGN KEY (grupo_id) REFERENCES grupos_permissoes(id),
    CONSTRAINT fk_grupo_permissao_permissao
        FOREIGN KEY (permissao_id) REFERENCES permissoes(id_permissao)
);

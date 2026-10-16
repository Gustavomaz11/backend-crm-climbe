ALTER TABLE empresas ADD COLUMN tipo_empresa VARCHAR(40) NULL;
ALTER TABLE empresas ADD COLUMN dados_cadastrais JSON NULL;

CREATE TABLE empresa_comprovantes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    cnpj VARCHAR(14) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL,
    mensagem VARCHAR(500) NULL,
    atualizado_em DATETIME(6) NOT NULL,
    gerado_em DATETIME(6) NULL,
    conteudo LONGBLOB NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_empresa_comprovante UNIQUE (empresa_id, tipo),
    CONSTRAINT fk_empresa_comprovante FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa) ON DELETE CASCADE
);
CREATE INDEX idx_empresa_comprovantes_fila ON empresa_comprovantes(status, id);

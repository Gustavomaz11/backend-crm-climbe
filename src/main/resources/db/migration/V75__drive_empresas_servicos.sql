CREATE TABLE empresa_drive (
    empresa_id BIGINT PRIMARY KEY,
    pasta_id VARCHAR(150) NULL,
    documentacoes_id VARCHAR(150) NULL,
    pendente BOOLEAN NOT NULL DEFAULT TRUE,
    tentar_em DATETIME(6) NOT NULL,
    erro VARCHAR(500) NULL,
    CONSTRAINT fk_drive_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa) ON DELETE CASCADE
);
CREATE INDEX idx_empresa_drive_fila ON empresa_drive(pendente, tentar_em);

CREATE TABLE empresa_drive_servicos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    servico VARCHAR(50) NOT NULL,
    pasta_id VARCHAR(150) NULL,
    CONSTRAINT uk_empresa_drive_servico UNIQUE (empresa_id, servico),
    CONSTRAINT fk_drive_servico_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa) ON DELETE CASCADE
);
ALTER TABLE empresa_comprovantes ADD COLUMN drive_arquivo_id VARCHAR(150) NULL;

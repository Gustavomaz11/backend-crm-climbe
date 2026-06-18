ALTER TABLE contratos
    MODIFY COLUMN proposta_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS historico_aprovacao_contratos (
    id_historico BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    status_anterior VARCHAR(255) NOT NULL,
    status_novo VARCHAR(255) NOT NULL,
    data_alteracao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historico_aprovacao_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato),
    CONSTRAINT fk_historico_aprovacao_contrato_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_historico_aprovacao_contrato (contrato_id),
    INDEX idx_historico_aprovacao_contrato_usuario (usuario_id)
);

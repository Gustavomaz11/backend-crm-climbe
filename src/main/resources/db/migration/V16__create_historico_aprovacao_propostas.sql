CREATE TABLE historico_aprovacao_propostas (
    id_historico BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposta_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    status_anterior VARCHAR(255) NOT NULL,
    status_novo VARCHAR(255) NOT NULL,
    data_alteracao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historico_aprovacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_historico_aprovacao_proposta (proposta_id),
    INDEX idx_historico_aprovacao_usuario (usuario_id)
);
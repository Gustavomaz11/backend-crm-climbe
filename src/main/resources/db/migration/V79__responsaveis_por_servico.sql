CREATE TABLE contrato_responsaveis_servico (
    contrato_id BIGINT NOT NULL,
    servico VARCHAR(50) NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (contrato_id, servico),
    CONSTRAINT fk_contrato_resp_servico_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato),
    CONSTRAINT fk_contrato_resp_servico_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_contrato_resp_servico_usuario ON contrato_responsaveis_servico (usuario_id);

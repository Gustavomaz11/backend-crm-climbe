CREATE TABLE contrato_servicos_encerrados (
    contrato_id BIGINT NOT NULL,
    servico VARCHAR(50) NOT NULL,
    encerrado_em DATETIME(6) NOT NULL,
    encerrado_por BIGINT NOT NULL,
    PRIMARY KEY (contrato_id, servico),
    CONSTRAINT fk_servico_encerrado_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato),
    CONSTRAINT fk_servico_encerrado_usuario FOREIGN KEY (encerrado_por) REFERENCES usuarios(id)
);

CREATE TABLE proposta_recebimento_servicos (
    proposta_id BIGINT NOT NULL,
    numero INT NOT NULL,
    servico VARCHAR(50) NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (proposta_id, numero, servico),
    CONSTRAINT ck_recebimento_servico_valor CHECK (valor >= 0),
    CONSTRAINT fk_recebimento_servico_proposta FOREIGN KEY (proposta_id) REFERENCES propostas(id_proposta) ON DELETE CASCADE
);

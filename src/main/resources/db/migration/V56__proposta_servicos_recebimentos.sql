CREATE TABLE proposta_servicos (
    proposta_id BIGINT NOT NULL,
    posicao INT NOT NULL,
    servico VARCHAR(50) NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    comissao_tecnico_percentual DECIMAL(5,2) NOT NULL,
    comissao_comercial_percentual DECIMAL(5,2) NOT NULL,
    PRIMARY KEY (proposta_id, posicao),
    UNIQUE KEY uk_proposta_servico (proposta_id, servico),
    CONSTRAINT fk_proposta_servico_proposta FOREIGN KEY (proposta_id) REFERENCES propostas(id_proposta) ON DELETE CASCADE
);

CREATE TABLE proposta_recebimentos (
    proposta_id BIGINT NOT NULL,
    numero INT NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (proposta_id, numero),
    CONSTRAINT fk_proposta_recebimento_proposta FOREIGN KEY (proposta_id) REFERENCES propostas(id_proposta) ON DELETE CASCADE
);

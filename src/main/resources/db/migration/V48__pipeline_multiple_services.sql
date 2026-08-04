CREATE TABLE pipeline_vendas_negocio_servicos (
    negocio_id BIGINT NOT NULL,
    servico VARCHAR(180) NOT NULL,
    ordem INT NOT NULL,
    PRIMARY KEY (negocio_id, ordem),
    UNIQUE KEY uk_pipeline_negocio_servico (negocio_id, servico),
    CONSTRAINT fk_pipeline_negocio_servicos_negocio
        FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio)
        ON DELETE CASCADE,
    INDEX idx_pipeline_negocio_servicos_servico (servico)
);

INSERT INTO pipeline_vendas_negocio_servicos (negocio_id, servico, ordem)
SELECT id_negocio, TRIM(servico_interesse), 0
FROM pipeline_vendas_negocios
WHERE servico_interesse IS NOT NULL
  AND TRIM(servico_interesse) <> '';

ALTER TABLE propostas
    ADD COLUMN negocio_id BIGINT NULL AFTER empresa_id,
    ADD CONSTRAINT fk_proposta_pipeline_negocio
        FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio)
        ON DELETE SET NULL,
    ADD INDEX idx_propostas_negocio (negocio_id),
    ADD INDEX idx_propostas_empresa_criacao (empresa_id, data_criacao, id_proposta);

UPDATE pipeline_vendas_etapas
SET campos_obrigatorios = JSON_REMOVE(
        campos_obrigatorios,
        JSON_UNQUOTE(JSON_SEARCH(campos_obrigatorios, 'one', 'servicoInteresse'))
    )
WHERE JSON_SEARCH(campos_obrigatorios, 'one', 'servicoInteresse') IS NOT NULL;

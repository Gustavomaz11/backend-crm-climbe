ALTER TABLE contratos
    ADD COLUMN etapa_preparacao VARCHAR(30) NULL,
    ADD COLUMN responsavel_comercial_id BIGINT NULL,
    ADD CONSTRAINT fk_contrato_responsavel_comercial
        FOREIGN KEY (responsavel_comercial_id) REFERENCES usuarios(id);

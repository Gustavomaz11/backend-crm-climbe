ALTER TABLE pipeline_vendas_comentarios
    ADD COLUMN comentario_pai_id BIGINT NULL AFTER autor_id,
    ADD CONSTRAINT fk_pipeline_comentario_pai
        FOREIGN KEY (comentario_pai_id)
        REFERENCES pipeline_vendas_comentarios(id_comentario)
        ON DELETE CASCADE,
    ADD INDEX idx_pipeline_comentario_pai (comentario_pai_id);

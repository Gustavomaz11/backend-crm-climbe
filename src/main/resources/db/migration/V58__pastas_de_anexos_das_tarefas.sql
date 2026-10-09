CREATE TABLE tarefa_pastas (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contrato_task_id BIGINT NULL,
    pipeline_tarefa_id BIGINT NULL,
    pasta_pai_id BIGINT NULL,
    autor_id BIGINT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    criado_em DATETIME NOT NULL,
    CONSTRAINT ck_pasta_tarefa CHECK ((contrato_task_id IS NOT NULL AND pipeline_tarefa_id IS NULL) OR (contrato_task_id IS NULL AND pipeline_tarefa_id IS NOT NULL)),
    CONSTRAINT fk_pasta_contrato_task FOREIGN KEY (contrato_task_id) REFERENCES contrato_kanban_tasks(id_task) ON DELETE CASCADE,
    CONSTRAINT fk_pasta_pipeline_tarefa FOREIGN KEY (pipeline_tarefa_id) REFERENCES pipeline_vendas_tarefas(id_tarefa) ON DELETE CASCADE,
    CONSTRAINT fk_pasta_pai FOREIGN KEY (pasta_pai_id) REFERENCES tarefa_pastas(id) ON DELETE CASCADE,
    CONSTRAINT fk_pasta_autor FOREIGN KEY (autor_id) REFERENCES usuarios(id),
    INDEX idx_pastas_contrato (contrato_task_id, pasta_pai_id, nome),
    INDEX idx_pastas_pipeline (pipeline_tarefa_id, pasta_pai_id, nome)
);
ALTER TABLE tarefa_anexos ADD COLUMN pasta_id BIGINT NULL;
ALTER TABLE tarefa_anexos ADD CONSTRAINT fk_anexo_pasta FOREIGN KEY (pasta_id) REFERENCES tarefa_pastas(id) ON DELETE CASCADE;
ALTER TABLE tarefa_anexos ADD CONSTRAINT ck_anexo_pasta_comentario CHECK (pasta_id IS NULL OR comentario_id IS NULL);

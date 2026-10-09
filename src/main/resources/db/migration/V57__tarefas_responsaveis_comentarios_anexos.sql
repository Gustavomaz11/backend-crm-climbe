CREATE TABLE contrato_kanban_task_responsaveis (
    task_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, usuario_id),
    CONSTRAINT fk_task_responsaveis_task FOREIGN KEY (task_id) REFERENCES contrato_kanban_tasks(id_task) ON DELETE CASCADE,
    CONSTRAINT fk_task_responsaveis_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
INSERT INTO contrato_kanban_task_responsaveis (task_id, usuario_id)
SELECT id_task, id_responsavel FROM contrato_kanban_tasks WHERE id_responsavel IS NOT NULL;

CREATE TABLE pipeline_tarefa_responsaveis (
    tarefa_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (tarefa_id, usuario_id),
    CONSTRAINT fk_pipeline_tarefa_resp_tarefa FOREIGN KEY (tarefa_id) REFERENCES pipeline_vendas_tarefas(id_tarefa) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_tarefa_resp_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
INSERT INTO pipeline_tarefa_responsaveis (tarefa_id, usuario_id)
SELECT id_tarefa, responsavel_id FROM pipeline_vendas_tarefas;

ALTER TABLE contrato_kanban_subtarefas ADD COLUMN responsavel_id BIGINT NULL;
ALTER TABLE contrato_kanban_subtarefas ADD CONSTRAINT fk_contrato_subtarefa_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuarios(id);
ALTER TABLE pipeline_vendas_subtarefas ADD COLUMN responsavel_id BIGINT NULL;
ALTER TABLE pipeline_vendas_subtarefas ADD CONSTRAINT fk_pipeline_subtarefa_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuarios(id);

CREATE TABLE tarefa_comentarios (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contrato_task_id BIGINT NULL,
    pipeline_tarefa_id BIGINT NULL,
    autor_id BIGINT NOT NULL,
    comentario_pai_id BIGINT NULL,
    conteudo TEXT NOT NULL,
    criado_em DATETIME NOT NULL,
    CONSTRAINT ck_comentario_tarefa CHECK ((contrato_task_id IS NOT NULL AND pipeline_tarefa_id IS NULL) OR (contrato_task_id IS NULL AND pipeline_tarefa_id IS NOT NULL)),
    CONSTRAINT fk_comentario_contrato_task FOREIGN KEY (contrato_task_id) REFERENCES contrato_kanban_tasks(id_task) ON DELETE CASCADE,
    CONSTRAINT fk_comentario_pipeline_tarefa FOREIGN KEY (pipeline_tarefa_id) REFERENCES pipeline_vendas_tarefas(id_tarefa) ON DELETE CASCADE,
    CONSTRAINT fk_comentario_autor FOREIGN KEY (autor_id) REFERENCES usuarios(id),
    CONSTRAINT fk_comentario_pai FOREIGN KEY (comentario_pai_id) REFERENCES tarefa_comentarios(id) ON DELETE CASCADE,
    INDEX idx_comentarios_contrato (contrato_task_id, criado_em, id),
    INDEX idx_comentarios_pipeline (pipeline_tarefa_id, criado_em, id)
);
CREATE TABLE tarefa_anexos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contrato_task_id BIGINT NULL,
    pipeline_tarefa_id BIGINT NULL,
    comentario_id BIGINT NULL,
    autor_id BIGINT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    tamanho BIGINT NOT NULL,
    chave VARCHAR(1000) NOT NULL,
    criado_em DATETIME NOT NULL,
    CONSTRAINT ck_anexo_tarefa CHECK ((contrato_task_id IS NOT NULL AND pipeline_tarefa_id IS NULL) OR (contrato_task_id IS NULL AND pipeline_tarefa_id IS NOT NULL)),
    CONSTRAINT fk_anexo_contrato_task FOREIGN KEY (contrato_task_id) REFERENCES contrato_kanban_tasks(id_task) ON DELETE CASCADE,
    CONSTRAINT fk_anexo_pipeline_tarefa FOREIGN KEY (pipeline_tarefa_id) REFERENCES pipeline_vendas_tarefas(id_tarefa) ON DELETE CASCADE,
    CONSTRAINT fk_anexo_comentario FOREIGN KEY (comentario_id) REFERENCES tarefa_comentarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_anexo_autor FOREIGN KEY (autor_id) REFERENCES usuarios(id),
    INDEX idx_anexos_contrato (contrato_task_id, criado_em, id),
    INDEX idx_anexos_pipeline (pipeline_tarefa_id, criado_em, id)
);

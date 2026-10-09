ALTER TABLE contratos ADD COLUMN equipe_configurada BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE contrato_kanban_raias ADD COLUMN conclui_tarefas BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE contrato_kanban_raias SET conclui_tarefas = TRUE WHERE LOWER(TRIM(titulo)) IN ('concluído', 'concluido', 'concluídos', 'concluidos', 'concluída', 'concluida', 'concluídas', 'concluidas');
ALTER TABLE contrato_kanban_tasks ADD COLUMN concluida_em DATETIME NULL;

CREATE TABLE contrato_equipe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    CONSTRAINT fk_contrato_equipe_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato) ON DELETE CASCADE,
    CONSTRAINT fk_contrato_equipe_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uk_contrato_equipe UNIQUE (contrato_id, usuario_id)
);

CREATE TABLE contrato_tarefa_apoios (
    task_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, usuario_id),
    CONSTRAINT fk_tarefa_apoio_task FOREIGN KEY (task_id) REFERENCES contrato_kanban_tasks(id_task) ON DELETE CASCADE,
    CONSTRAINT fk_tarefa_apoio_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE contrato_rateio_tecnico_participantes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    competencia DATE NOT NULL,
    CONSTRAINT fk_rateio_tecnico_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato) ON DELETE CASCADE,
    CONSTRAINT fk_rateio_tecnico_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uk_rateio_tecnico_participante UNIQUE (contrato_id, usuario_id, competencia)
);

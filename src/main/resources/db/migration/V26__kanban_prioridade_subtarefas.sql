ALTER TABLE contrato_kanban_tasks
    ADD COLUMN prioridade VARCHAR(20) NOT NULL DEFAULT 'MEDIA';

CREATE TABLE IF NOT EXISTS contrato_kanban_subtarefas (
    id_subtarefa BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    titulo VARCHAR(180) NOT NULL,
    concluida BOOLEAN NOT NULL DEFAULT FALSE,
    posicao INT NOT NULL DEFAULT 0,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_contrato_kanban_subtarefa_task
        FOREIGN KEY (task_id) REFERENCES contrato_kanban_tasks(id_task)
        ON DELETE CASCADE,
    INDEX idx_contrato_kanban_subtarefa_task_posicao (task_id, posicao)
);

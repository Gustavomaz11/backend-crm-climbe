ALTER TABLE contrato_kanban_tasks ADD COLUMN justificativa_atraso VARCHAR(4000) NULL;
ALTER TABLE contrato_kanban_tasks ADD COLUMN justificada_em DATETIME NULL;
ALTER TABLE contrato_kanban_tasks ADD COLUMN justificada_por BIGINT NULL;

ALTER TABLE pipeline_vendas_tarefas ADD COLUMN justificativa_atraso VARCHAR(4000) NULL;
ALTER TABLE pipeline_vendas_tarefas ADD COLUMN justificada_em DATETIME NULL;
ALTER TABLE pipeline_vendas_tarefas ADD COLUMN justificada_por BIGINT NULL;

CREATE TABLE kanban_prazo_notificacoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chave VARCHAR(200) NOT NULL UNIQUE,
    enviado_em DATETIME NOT NULL
);

CREATE INDEX idx_contrato_task_prazo ON contrato_kanban_tasks (data_fim, raia_id);

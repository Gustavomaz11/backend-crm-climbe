CREATE TABLE contrato_apoio_atuacoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    usuario_id BIGINT NOT NULL,
    inicio DATETIME NOT NULL,
    fim DATETIME NULL,
    CONSTRAINT fk_apoio_atuacao_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato) ON DELETE CASCADE,
    CONSTRAINT fk_apoio_atuacao_tarefa FOREIGN KEY (task_id) REFERENCES contrato_kanban_tasks(id_task) ON DELETE SET NULL,
    CONSTRAINT fk_apoio_atuacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_apoio_atuacao_contrato_periodo (contrato_id, inicio, fim),
    INDEX idx_apoio_atuacao_tarefa (task_id)
);

-- O vínculo legado não armazenava a data de atribuição; usa a criação da tarefa como referência.
INSERT INTO contrato_apoio_atuacoes (contrato_id, task_id, usuario_id, inicio, fim)
SELECT t.contrato_id, t.id_task, a.usuario_id, t.criado_em,
       CASE WHEN r.conclui_tarefas THEN COALESCE(t.concluida_em, CURRENT_TIMESTAMP) ELSE NULL END
FROM contrato_tarefa_apoios a
JOIN contrato_kanban_tasks t ON t.id_task = a.task_id
JOIN contrato_kanban_raias r ON r.id_raia = t.raia_id
WHERE t.id_responsavel = a.usuario_id OR EXISTS (
    SELECT 1 FROM contrato_kanban_task_responsaveis responsaveis
    WHERE responsaveis.task_id = t.id_task AND responsaveis.usuario_id = a.usuario_id
);

CREATE TABLE pipeline_tarefa_notificacoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tarefa_id BIGINT NULL,
    destinatario_usuario_id BIGINT NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    referencia VARCHAR(30) NOT NULL,
    chave VARCHAR(190) NOT NULL,
    enviado_em DATETIME NOT NULL,
    CONSTRAINT fk_pipeline_tarefa_notificacao_tarefa
        FOREIGN KEY (tarefa_id) REFERENCES pipeline_vendas_tarefas(id_tarefa) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_tarefa_notificacao_destinatario
        FOREIGN KEY (destinatario_usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT uk_pipeline_tarefa_notificacao_chave UNIQUE (chave),
    INDEX idx_pipeline_tarefa_notificacao_destinatario_data (destinatario_usuario_id, enviado_em)
);

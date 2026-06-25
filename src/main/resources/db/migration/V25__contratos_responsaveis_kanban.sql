ALTER TABLE contratos
    ADD COLUMN id_responsavel BIGINT NULL;

ALTER TABLE contratos
    ADD CONSTRAINT fk_contrato_responsavel
        FOREIGN KEY (id_responsavel) REFERENCES usuarios(id);

CREATE TABLE IF NOT EXISTS contrato_participantes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    CONSTRAINT fk_contrato_participante_contrato
        FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato)
        ON DELETE CASCADE,
    CONSTRAINT fk_contrato_participante_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    UNIQUE KEY uk_contrato_participante (contrato_id, usuario_id),
    INDEX idx_contrato_participante_usuario (usuario_id)
);

CREATE TABLE IF NOT EXISTS contrato_kanban_raias (
    id_raia BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    posicao INT NOT NULL DEFAULT 0,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_contrato_kanban_raia_contrato
        FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato)
        ON DELETE CASCADE,
    INDEX idx_contrato_kanban_raia_contrato_posicao (contrato_id, posicao)
);

CREATE TABLE IF NOT EXISTS contrato_kanban_tasks (
    id_task BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    raia_id BIGINT NOT NULL,
    titulo VARCHAR(180) NOT NULL,
    descricao TEXT NULL,
    id_responsavel BIGINT NULL,
    data_inicio DATE NULL,
    data_fim DATE NULL,
    posicao INT NOT NULL DEFAULT 0,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_contrato_kanban_task_contrato
        FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato)
        ON DELETE CASCADE,
    CONSTRAINT fk_contrato_kanban_task_raia
        FOREIGN KEY (raia_id) REFERENCES contrato_kanban_raias(id_raia)
        ON DELETE CASCADE,
    CONSTRAINT fk_contrato_kanban_task_responsavel
        FOREIGN KEY (id_responsavel) REFERENCES usuarios(id),
    INDEX idx_contrato_kanban_task_contrato (contrato_id),
    INDEX idx_contrato_kanban_task_raia_posicao (raia_id, posicao),
    INDEX idx_contrato_kanban_task_responsavel (id_responsavel)
);

INSERT INTO permissoes (descricao, codigo)
SELECT 'Exibição do painel de tarefas dos contratos', 'CONTRATO_KANBAN'
WHERE NOT EXISTS (SELECT 1 FROM permissoes WHERE codigo = 'CONTRATO_KANBAN');

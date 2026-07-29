CREATE TABLE pipeline_scripts (
    id_script BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(160) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    canal VARCHAR(30) NOT NULL,
    modelo_mensagem TEXT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por BIGINT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_script_criador FOREIGN KEY (criado_por) REFERENCES usuarios(id),
    INDEX idx_pipeline_script_categoria_canal (categoria, canal),
    INDEX idx_pipeline_script_ativo (ativo)
);

CREATE TABLE pipeline_campanhas (
    id_campanha BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(160) NOT NULL,
    estrategia VARCHAR(180) NOT NULL,
    descricao VARCHAR(500) NULL,
    ativo BOOLEAN NOT NULL DEFAULT FALSE,
    criado_por BIGINT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_campanha_criador FOREIGN KEY (criado_por) REFERENCES usuarios(id),
    INDEX idx_pipeline_campanha_ativo (ativo),
    INDEX idx_pipeline_campanha_estrategia (estrategia)
);

CREATE TABLE pipeline_campanha_leads (
    campanha_id BIGINT NOT NULL,
    negocio_id BIGINT NOT NULL,
    PRIMARY KEY (campanha_id, negocio_id),
    CONSTRAINT fk_pipeline_campanha_lead_campanha FOREIGN KEY (campanha_id) REFERENCES pipeline_campanhas(id_campanha) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_campanha_lead_negocio FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio) ON DELETE CASCADE
);

CREATE TABLE pipeline_campanha_participantes (
    campanha_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (campanha_id, usuario_id),
    CONSTRAINT fk_pipeline_campanha_participante_campanha FOREIGN KEY (campanha_id) REFERENCES pipeline_campanhas(id_campanha) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_campanha_participante_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE pipeline_campanha_scripts (
    campanha_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    PRIMARY KEY (campanha_id, script_id),
    CONSTRAINT fk_pipeline_campanha_script_campanha FOREIGN KEY (campanha_id) REFERENCES pipeline_campanhas(id_campanha) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_campanha_script_script FOREIGN KEY (script_id) REFERENCES pipeline_scripts(id_script)
);

CREATE TABLE pipeline_campanha_dias_execucao (
    campanha_id BIGINT NOT NULL,
    dia_semana INT NOT NULL,
    PRIMARY KEY (campanha_id, dia_semana),
    CONSTRAINT fk_pipeline_campanha_dia_campanha FOREIGN KEY (campanha_id) REFERENCES pipeline_campanhas(id_campanha) ON DELETE CASCADE
);

CREATE TABLE pipeline_cadencia_etapas (
    id_etapa_cadencia BIGINT AUTO_INCREMENT PRIMARY KEY,
    campanha_id BIGINT NOT NULL,
    ordem INT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(180) NULL,
    descricao TEXT NULL,
    tipo_tarefa VARCHAR(100) NULL,
    prioridade VARCHAR(20) NULL,
    dias_uteis_espera INT NOT NULL DEFAULT 0,
    prazo_dias_uteis INT NULL,
    script_id BIGINT NULL,
    CONSTRAINT fk_pipeline_cadencia_campanha FOREIGN KEY (campanha_id) REFERENCES pipeline_campanhas(id_campanha) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_cadencia_script FOREIGN KEY (script_id) REFERENCES pipeline_scripts(id_script),
    UNIQUE KEY uk_pipeline_cadencia_ordem (campanha_id, ordem)
);

CREATE TABLE pipeline_campanha_execucoes (
    id_execucao BIGINT AUTO_INCREMENT PRIMARY KEY,
    campanha_id BIGINT NOT NULL,
    negocio_id BIGINT NOT NULL,
    participante_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    ordem_atual INT NOT NULL DEFAULT 0,
    proxima_execucao_em DATETIME NULL,
    tarefa_atual_id BIGINT NULL,
    iniciado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalizado_em DATETIME NULL,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_execucao_campanha FOREIGN KEY (campanha_id) REFERENCES pipeline_campanhas(id_campanha) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_execucao_negocio FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_execucao_participante FOREIGN KEY (participante_id) REFERENCES usuarios(id),
    CONSTRAINT fk_pipeline_execucao_tarefa FOREIGN KEY (tarefa_atual_id) REFERENCES pipeline_vendas_tarefas(id_tarefa) ON DELETE SET NULL,
    UNIQUE KEY uk_pipeline_execucao_lead (campanha_id, negocio_id),
    INDEX idx_pipeline_execucao_processamento (status, proxima_execucao_em)
);

ALTER TABLE pipeline_vendas_tarefas
    ADD COLUMN campanha_id BIGINT NULL AFTER negocio_id,
    ADD COLUMN etapa_cadencia_id BIGINT NULL AFTER campanha_id,
    ADD COLUMN script_id BIGINT NULL AFTER etapa_cadencia_id,
    ADD CONSTRAINT fk_pipeline_tarefa_campanha FOREIGN KEY (campanha_id) REFERENCES pipeline_campanhas(id_campanha) ON DELETE SET NULL,
    ADD CONSTRAINT fk_pipeline_tarefa_cadencia FOREIGN KEY (etapa_cadencia_id) REFERENCES pipeline_cadencia_etapas(id_etapa_cadencia) ON DELETE SET NULL,
    ADD CONSTRAINT fk_pipeline_tarefa_script FOREIGN KEY (script_id) REFERENCES pipeline_scripts(id_script) ON DELETE SET NULL,
    ADD INDEX idx_pipeline_tarefa_script_status (script_id, status),
    ADD INDEX idx_pipeline_tarefa_campanha (campanha_id);

INSERT INTO permissoes (descricao, codigo) VALUES
    ('Comercial — visualizar campanhas', 'COMERCIAL_CAMPANHA_VISUALIZAR'),
    ('Comercial — criar campanhas', 'COMERCIAL_CAMPANHA_CRIAR'),
    ('Comercial — editar campanhas', 'COMERCIAL_CAMPANHA_EDITAR'),
    ('Comercial — ativar e inativar campanhas', 'COMERCIAL_CAMPANHA_EXECUTAR'),
    ('Comercial — visualizar biblioteca de scripts', 'COMERCIAL_SCRIPT_VISUALIZAR'),
    ('Comercial — criar scripts', 'COMERCIAL_SCRIPT_CRIAR'),
    ('Comercial — editar scripts', 'COMERCIAL_SCRIPT_EDITAR');

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
JOIN permissoes permissao ON permissao.codigo IN (
    'COMERCIAL_CAMPANHA_VISUALIZAR', 'COMERCIAL_CAMPANHA_CRIAR',
    'COMERCIAL_CAMPANHA_EDITAR', 'COMERCIAL_CAMPANHA_EXECUTAR',
    'COMERCIAL_SCRIPT_VISUALIZAR', 'COMERCIAL_SCRIPT_CRIAR', 'COMERCIAL_SCRIPT_EDITAR'
)
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'teste@climb.com' AND usuario_permissao.id IS NULL;

CREATE TABLE pipeline_vendas_tarefas (
    id_tarefa BIGINT AUTO_INCREMENT PRIMARY KEY,
    negocio_id BIGINT NOT NULL,
    titulo VARCHAR(180) NOT NULL,
    descricao TEXT NULL,
    responsavel_id BIGINT NOT NULL,
    data_inicio DATE NULL,
    prazo DATE NULL,
    prioridade VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    tipo VARCHAR(100) NOT NULL,
    observacoes TEXT NULL,
    criado_por BIGINT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    concluido_em DATETIME NULL,
    CONSTRAINT fk_pipeline_tarefa_negocio
        FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_tarefa_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios(id),
    CONSTRAINT fk_pipeline_tarefa_criador
        FOREIGN KEY (criado_por) REFERENCES usuarios(id),
    INDEX idx_pipeline_tarefa_negocio (negocio_id),
    INDEX idx_pipeline_tarefa_responsavel (responsavel_id),
    INDEX idx_pipeline_tarefa_prazo_status (prazo, status),
    INDEX idx_pipeline_tarefa_tipo (tipo)
);

CREATE TABLE pipeline_vendas_subtarefas (
    id_subtarefa BIGINT AUTO_INCREMENT PRIMARY KEY,
    tarefa_id BIGINT NOT NULL,
    titulo VARCHAR(180) NOT NULL,
    concluida BOOLEAN NOT NULL DEFAULT FALSE,
    posicao INT NOT NULL DEFAULT 0,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_subtarefa_tarefa
        FOREIGN KEY (tarefa_id) REFERENCES pipeline_vendas_tarefas(id_tarefa) ON DELETE CASCADE,
    INDEX idx_pipeline_subtarefa_tarefa_posicao (tarefa_id, posicao)
);

CREATE TABLE pipeline_vendas_comentarios (
    id_comentario BIGINT AUTO_INCREMENT PRIMARY KEY,
    negocio_id BIGINT NOT NULL,
    autor_id BIGINT NOT NULL,
    conteudo TEXT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_comentario_negocio
        FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_comentario_autor
        FOREIGN KEY (autor_id) REFERENCES usuarios(id),
    INDEX idx_pipeline_comentario_negocio_data (negocio_id, criado_em)
);

CREATE TABLE pipeline_vendas_historico (
    id_historico BIGINT AUTO_INCREMENT PRIMARY KEY,
    negocio_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    tipo_evento VARCHAR(50) NOT NULL,
    descricao TEXT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_historico_negocio
        FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_historico_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_pipeline_historico_negocio_data (negocio_id, criado_em)
);

INSERT INTO pipeline_vendas_historico (negocio_id, usuario_id, tipo_evento, descricao, criado_em)
SELECT id_negocio, criado_por, 'CRIACAO_NEGOCIO', CONCAT('Negócio criado na etapa ', etapa.nome), negocio.criado_em
FROM pipeline_vendas_negocios negocio
JOIN pipeline_vendas_etapas etapa ON etapa.id_etapa = negocio.etapa_id;

INSERT INTO permissoes (descricao, codigo) VALUES
    ('Comercial — visualizar tarefas dos negócios', 'COMERCIAL_TAREFA_VISUALIZAR'),
    ('Comercial — criar tarefas nos negócios', 'COMERCIAL_TAREFA_CRIAR'),
    ('Comercial — editar tarefas dos negócios', 'COMERCIAL_TAREFA_EDITAR'),
    ('Comercial — concluir tarefas dos negócios', 'COMERCIAL_TAREFA_CONCLUIR'),
    ('Comercial — visualizar comentários dos negócios', 'COMERCIAL_COMENTARIO_VISUALIZAR'),
    ('Comercial — criar comentários nos negócios', 'COMERCIAL_COMENTARIO_CRIAR'),
    ('Comercial — visualizar histórico dos negócios', 'COMERCIAL_HISTORICO_VISUALIZAR');

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
JOIN permissoes permissao ON permissao.codigo IN (
    'COMERCIAL_TAREFA_VISUALIZAR',
    'COMERCIAL_TAREFA_CRIAR',
    'COMERCIAL_TAREFA_EDITAR',
    'COMERCIAL_TAREFA_CONCLUIR',
    'COMERCIAL_COMENTARIO_VISUALIZAR',
    'COMERCIAL_COMENTARIO_CRIAR',
    'COMERCIAL_HISTORICO_VISUALIZAR'
)
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'teste@climb.com'
  AND usuario_permissao.id IS NULL;

CREATE TABLE pipeline_vendas_etapas (
    id_etapa BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL UNIQUE,
    nome VARCHAR(120) NOT NULL,
    posicao INT NOT NULL,
    resultado VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pipeline_etapas_posicao (posicao)
);

INSERT INTO pipeline_vendas_etapas (codigo, nome, posicao, resultado) VALUES
    ('REUNIAO_MARCADA', 'Reunião marcada', 1, 'ABERTO'),
    ('REUNIAO_REALIZADA', 'Reunião realizada', 2, 'ABERTO'),
    ('DIAGNOSTICO', 'Diagnóstico', 3, 'ABERTO'),
    ('PROPOSTA_EM_ELABORACAO', 'Proposta em elaboração', 4, 'ABERTO'),
    ('PROPOSTA_APRESENTADA', 'Proposta apresentada', 5, 'ABERTO'),
    ('FOLLOW_UP', 'Follow-up', 6, 'ABERTO'),
    ('NEGOCIACAO', 'Negociação', 7, 'ABERTO'),
    ('FECHADO', 'Fechado', 8, 'GANHO'),
    ('PERDIDO', 'Perdido', 9, 'PERDIDO');

CREATE TABLE pipeline_vendas_negocios (
    id_negocio BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NULL,
    nome_empresa VARCHAR(180) NOT NULL,
    nome_contato VARCHAR(180) NOT NULL,
    telefone VARCHAR(50) NOT NULL,
    email VARCHAR(180) NOT NULL,
    responsavel_id BIGINT NOT NULL,
    etapa_id BIGINT NOT NULL,
    data_reuniao DATETIME NULL,
    origem_negocio VARCHAR(120) NOT NULL,
    estrategia_comercial TEXT NOT NULL,
    servico_interesse VARCHAR(180) NOT NULL,
    valor_estimado_proposta DECIMAL(15, 2) NULL,
    observacoes TEXT NULL,
    resultado VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    contrato_id BIGINT NULL UNIQUE,
    criado_por BIGINT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultima_movimentacao_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_negocio_empresa
        FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa),
    CONSTRAINT fk_pipeline_negocio_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios(id),
    CONSTRAINT fk_pipeline_negocio_etapa
        FOREIGN KEY (etapa_id) REFERENCES pipeline_vendas_etapas(id_etapa),
    CONSTRAINT fk_pipeline_negocio_contrato
        FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato),
    CONSTRAINT fk_pipeline_negocio_criador
        FOREIGN KEY (criado_por) REFERENCES usuarios(id),
    INDEX idx_pipeline_negocio_etapa_movimentacao (etapa_id, ultima_movimentacao_em),
    INDEX idx_pipeline_negocio_responsavel (responsavel_id),
    INDEX idx_pipeline_negocio_empresa (empresa_id),
    INDEX idx_pipeline_negocio_resultado (resultado)
);

INSERT INTO permissoes (descricao, codigo) VALUES
    ('Comercial — visualizar o Pipeline de Vendas', 'COMERCIAL'),
    ('Comercial — criar negócios', 'COMERCIAL_CRIAR'),
    ('Comercial — editar negócios', 'COMERCIAL_EDITAR'),
    ('Comercial — movimentar negócios entre etapas', 'COMERCIAL_MOVIMENTAR'),
    ('Comercial — marcar negócios como ganhos ou perdidos', 'COMERCIAL_CONCLUIR'),
    ('Comercial — converter negócios ganhos em contratos', 'COMERCIAL_CONVERTER_CONTRATO');

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
JOIN permissoes permissao ON permissao.codigo IN (
    'COMERCIAL',
    'COMERCIAL_CRIAR',
    'COMERCIAL_EDITAR',
    'COMERCIAL_MOVIMENTAR',
    'COMERCIAL_CONCLUIR',
    'COMERCIAL_CONVERTER_CONTRATO'
)
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'teste@climb.com'
  AND usuario_permissao.id IS NULL;

CREATE TABLE pipeline_motivos_perda (
    id_motivo BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL UNIQUE,
    descricao VARCHAR(500) NULL,
    posicao INT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pipeline_motivo_ativo_posicao (ativo, posicao)
);

INSERT INTO pipeline_motivos_perda (nome, descricao, posicao, ativo) VALUES
    ('Sem interesse', 'O potencial cliente informou que não possui interesse.', 1, TRUE),
    ('Sem orçamento', 'Não há orçamento disponível para a contratação.', 2, TRUE),
    ('Não respondeu', 'Não houve retorno após as tentativas de contato.', 3, TRUE),
    ('Escolheu um concorrente', 'A oportunidade foi fechada com outro fornecedor.', 4, TRUE),
    ('Serviço não aderente', 'A necessidade não é atendida pelo portfólio atual.', 5, TRUE),
    ('Momento inadequado', 'Existe interesse, mas o momento não é adequado.', 6, TRUE),
    ('Negociação não aprovada', 'A negociação não recebeu aprovação interna.', 7, TRUE),
    ('Outro', 'Motivo não contemplado nas demais opções.', 8, TRUE);

ALTER TABLE pipeline_vendas_negocios
    ADD COLUMN motivo_perda_id BIGINT NULL AFTER resultado,
    ADD COLUMN observacao_perda VARCHAR(500) NULL AFTER motivo_perda_id,
    ADD COLUMN encerrado_em DATETIME NULL AFTER observacao_perda,
    ADD CONSTRAINT fk_pipeline_negocio_motivo_perda
        FOREIGN KEY (motivo_perda_id) REFERENCES pipeline_motivos_perda(id_motivo),
    ADD INDEX idx_pipeline_negocio_resultado_encerramento (resultado, encerrado_em),
    ADD INDEX idx_pipeline_negocio_responsavel_criacao (responsavel_id, criado_em),
    ADD INDEX idx_pipeline_negocio_funil_criacao (funil_id, criado_em);

UPDATE pipeline_vendas_negocios
SET encerrado_em = ultima_movimentacao_em
WHERE resultado IN ('GANHO', 'PERDIDO') AND encerrado_em IS NULL;

CREATE TABLE pipeline_vendas_movimentacoes_etapa (
    id_movimentacao BIGINT AUTO_INCREMENT PRIMARY KEY,
    negocio_id BIGINT NOT NULL,
    etapa_id BIGINT NOT NULL,
    entrada_em DATETIME NOT NULL,
    saida_em DATETIME NULL,
    CONSTRAINT fk_pipeline_movimentacao_negocio
        FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_movimentacao_etapa
        FOREIGN KEY (etapa_id) REFERENCES pipeline_vendas_etapas(id_etapa),
    INDEX idx_pipeline_movimentacao_negocio (negocio_id, entrada_em),
    INDEX idx_pipeline_movimentacao_etapa (etapa_id, entrada_em),
    INDEX idx_pipeline_movimentacao_aberta (negocio_id, saida_em)
);

INSERT INTO pipeline_vendas_movimentacoes_etapa (negocio_id, etapa_id, entrada_em, saida_em)
SELECT id_negocio, etapa_id, ultima_movimentacao_em,
       CASE WHEN resultado IN ('GANHO', 'PERDIDO') THEN encerrado_em ELSE NULL END
FROM pipeline_vendas_negocios;

INSERT INTO permissoes (descricao, codigo) VALUES
    ('Comercial — visualizar dashboard e indicadores', 'COMERCIAL_DASHBOARD_VISUALIZAR'),
    ('Comercial — visualizar motivos de perda', 'COMERCIAL_MOTIVO_PERDA_VISUALIZAR'),
    ('Comercial — criar motivos de perda', 'COMERCIAL_MOTIVO_PERDA_CRIAR'),
    ('Comercial — editar e inativar motivos de perda', 'COMERCIAL_MOTIVO_PERDA_EDITAR');

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT usuario.id, permissao.id_permissao
FROM usuarios usuario
JOIN permissoes permissao ON permissao.codigo IN (
    'COMERCIAL_DASHBOARD_VISUALIZAR',
    'COMERCIAL_MOTIVO_PERDA_VISUALIZAR',
    'COMERCIAL_MOTIVO_PERDA_CRIAR',
    'COMERCIAL_MOTIVO_PERDA_EDITAR'
)
LEFT JOIN usuario_permissoes usuario_permissao
    ON usuario_permissao.id_usuario = usuario.id
    AND usuario_permissao.id_permissao = permissao.id_permissao
WHERE usuario.email = 'teste@climb.com'
  AND usuario_permissao.id IS NULL;

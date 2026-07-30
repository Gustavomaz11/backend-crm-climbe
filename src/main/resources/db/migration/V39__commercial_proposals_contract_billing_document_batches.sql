ALTER TABLE propostas
    ADD COLUMN servico VARCHAR(50) NULL,
    ADD COLUMN mes_inicio DATE NULL,
    ADD COLUMN recorrencia_meses INT NULL,
    ADD COLUMN quantidade_parcelas INT NOT NULL DEFAULT 1,
    ADD COLUMN parcelas_iguais BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN comissao_tecnico_percentual DECIMAL(5,2) NULL,
    ADD COLUMN comissao_comercial_percentual DECIMAL(5,2) NULL,
    ADD COLUMN observacoes TEXT NULL;

CREATE TABLE proposta_reajustes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposta_id BIGINT NOT NULL,
    mes_vigencia INT NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_proposta_reajuste_proposta FOREIGN KEY (proposta_id) REFERENCES propostas(id_proposta) ON DELETE CASCADE,
    UNIQUE KEY uk_proposta_reajuste_mes (proposta_id, mes_vigencia)
);

CREATE TABLE proposta_equipe_tecnica (
    proposta_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (proposta_id, usuario_id),
    CONSTRAINT fk_proposta_equipe_tecnica_proposta FOREIGN KEY (proposta_id) REFERENCES propostas(id_proposta) ON DELETE CASCADE,
    CONSTRAINT fk_proposta_equipe_tecnica_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE proposta_equipe_comercial (
    proposta_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (proposta_id, usuario_id),
    CONSTRAINT fk_proposta_equipe_comercial_proposta FOREIGN KEY (proposta_id) REFERENCES propostas(id_proposta) ON DELETE CASCADE,
    CONSTRAINT fk_proposta_equipe_comercial_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

ALTER TABLE contratos
    ADD COLUMN servico VARCHAR(50) NULL,
    ADD COLUMN data_aprovacao DATE NULL;

CREATE TABLE contrato_parcelas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    numero INT NOT NULL,
    competencia DATE NOT NULL,
    vencimento DATE NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    CONSTRAINT fk_contrato_parcela_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato) ON DELETE CASCADE,
    UNIQUE KEY uk_contrato_parcela_numero (contrato_id, numero),
    INDEX idx_contrato_parcela_vencimento (vencimento, status)
);

CREATE TABLE documento_lotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    analista_id BIGINT NOT NULL,
    email_destinatario VARCHAR(255) NOT NULL,
    token_upload VARCHAR(100) NOT NULL,
    token_expira_em DATETIME NOT NULL,
    data_solicitacao DATETIME NOT NULL,
    CONSTRAINT fk_documento_lote_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa),
    CONSTRAINT fk_documento_lote_analista FOREIGN KEY (analista_id) REFERENCES usuarios(id),
    UNIQUE KEY uk_documento_lote_token (token_upload)
);

ALTER TABLE documentos
    ADD COLUMN lote_id BIGINT NULL,
    ADD CONSTRAINT fk_documento_lote FOREIGN KEY (lote_id) REFERENCES documento_lotes(id) ON DELETE CASCADE,
    ADD INDEX idx_documento_lote (lote_id);

INSERT INTO servicos (nome)
SELECT nome FROM (
    SELECT 'BPO' nome UNION ALL SELECT 'CFO' UNION ALL SELECT 'Contabilidade' UNION ALL
    SELECT 'Valuation' UNION ALL SELECT 'Finance Support' UNION ALL SELECT 'Consórcio' UNION ALL
    SELECT 'Desenvolvimento de Software'
) catalogo
WHERE NOT EXISTS (SELECT 1 FROM servicos s WHERE LOWER(s.nome) = LOWER(catalogo.nome));

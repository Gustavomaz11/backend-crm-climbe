ALTER TABLE solicitacoes_acesso
    ADD COLUMN excluido_em TIMESTAMP NULL;

ALTER TABLE solicitacoes_acesso
    ADD COLUMN excluido_por BIGINT NULL;

ALTER TABLE solicitacoes_acesso
    ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

ALTER TABLE solicitacoes_acesso
    ADD CONSTRAINT fk_solicitacoes_acesso_excluido_por
        FOREIGN KEY (excluido_por) REFERENCES usuarios(id) ON DELETE SET NULL;

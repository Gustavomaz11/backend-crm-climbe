ALTER TABLE notificacoes
    ADD COLUMN lida BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE notificacoes
    ADD COLUMN data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE notificacoes
    ADD COLUMN data_leitura TIMESTAMP NULL;

CREATE INDEX idx_notificacoes_usuario_lida
    ON notificacoes (id_usuario, lida);

CREATE INDEX idx_notificacoes_usuario_data_criacao
    ON notificacoes (id_usuario, data_criacao);

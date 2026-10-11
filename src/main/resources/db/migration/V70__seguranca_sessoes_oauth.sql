ALTER TABLE usuarios ADD COLUMN versao_autenticacao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN versao_registro BIGINT NOT NULL DEFAULT 0;

CREATE TABLE sessoes_autenticacao (
    id_sessao VARCHAR(36) PRIMARY KEY,
    id_usuario BIGINT NOT NULL,
    versao_autenticacao BIGINT NOT NULL,
    refresh_hash VARCHAR(64) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    expira_em DATETIME(6) NOT NULL,
    revogada_em DATETIME(6),
    CONSTRAINT fk_sessao_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id) ON DELETE CASCADE,
    INDEX idx_sessao_usuario (id_usuario), INDEX idx_sessao_expiracao (expira_em)
);

CREATE TABLE oauth_login_requests (
    state_hash VARCHAR(64) PRIMARY KEY,
    browser_hash VARCHAR(64) NOT NULL,
    code_verifier VARCHAR(128) NOT NULL,
    app_challenge VARCHAR(43) NOT NULL,
    expira_em DATETIME(6) NOT NULL,
    INDEX idx_oauth_login_expiracao (expira_em)
);

ALTER TABLE oauth2_exchange_codes ADD COLUMN app_challenge VARCHAR(43);

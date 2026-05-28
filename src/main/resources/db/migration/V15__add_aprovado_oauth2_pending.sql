ALTER TABLE oauth2_pending_registrations
    ADD COLUMN aprovado BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE oauth2_pending_registrations
    ADD COLUMN aprovado_em TIMESTAMP NULL;

ALTER TABLE oauth2_pending_registrations
    ADD COLUMN aprovado_por BIGINT NULL;

CREATE INDEX idx_oauth2_pending_listagem
    ON oauth2_pending_registrations (consumido, aprovado, expira_em);

ALTER TABLE oauth2_pending_registrations
    ADD CONSTRAINT fk_oauth2_pending_aprovado_por
    FOREIGN KEY (aprovado_por) REFERENCES usuarios(id);

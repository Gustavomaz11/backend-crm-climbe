ALTER TABLE oauth2_pending_registrations
    ADD COLUMN cargo_id BIGINT NULL;

ALTER TABLE oauth2_pending_registrations
    ADD CONSTRAINT fk_oauth2_pending_cargo
    FOREIGN KEY (cargo_id) REFERENCES cargos(id);

CREATE TABLE oauth2_pending_permissoes (
    pending_id BIGINT NOT NULL,
    id_permissao BIGINT NOT NULL,
    PRIMARY KEY (pending_id, id_permissao),
    CONSTRAINT fk_oauth2_pending_permissoes_pending
        FOREIGN KEY (pending_id) REFERENCES oauth2_pending_registrations(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth2_pending_permissoes_permissao
        FOREIGN KEY (id_permissao) REFERENCES permissoes(id_permissao)
);

-- Aprovações antigas ainda não consumidas precisam passar pela nova atribuição obrigatória.
UPDATE oauth2_pending_registrations
SET aprovado = FALSE,
    aprovado_em = NULL,
    aprovado_por = NULL
WHERE aprovado = TRUE
  AND consumido = FALSE;

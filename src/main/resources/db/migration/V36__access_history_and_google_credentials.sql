CREATE TABLE solicitacoes_acesso (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    origem VARCHAR(20) NOT NULL,
    referencia_id BIGINT NOT NULL,
    nome_completo VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    cpf VARCHAR(255),
    contato VARCHAR(255),
    cargo_nome VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_em TIMESTAMP NULL,
    decidido_em TIMESTAMP NULL,
    decidido_por BIGINT NULL,
    CONSTRAINT uk_solicitacoes_acesso_origem_referencia UNIQUE (origem, referencia_id),
    CONSTRAINT fk_solicitacoes_acesso_decidido_por
        FOREIGN KEY (decidido_por) REFERENCES usuarios(id) ON DELETE SET NULL
);

CREATE INDEX idx_solicitacoes_acesso_status_criado
    ON solicitacoes_acesso (status, criado_em);

INSERT INTO solicitacoes_acesso (
    origem, referencia_id, nome_completo, email, cpf, contato, cargo_nome,
    status, criado_em, decidido_em
)
SELECT
    'USUARIO', u.id, u.nome_completo, u.email, u.cpf, u.contato, c.nome,
    CASE
        WHEN u.situacao = 'ESPERANDO_APROVACAO' THEN 'PENDENTE'
        WHEN u.situacao = 'ATIVO' THEN 'APROVADO'
        ELSE 'RECUSADO'
    END,
    CURRENT_TIMESTAMP,
    CASE WHEN u.situacao = 'ESPERANDO_APROVACAO' THEN NULL ELSE CURRENT_TIMESTAMP END
FROM usuarios u
LEFT JOIN cargos c ON c.id = u.cargo_id
WHERE u.situacao IN ('ESPERANDO_APROVACAO', 'ATIVO', 'INATIVO')
  AND u.email <> 'administrador@climbe.com';

INSERT INTO solicitacoes_acesso (
    origem, referencia_id, nome_completo, email, avatar_url, cargo_nome,
    status, criado_em, expira_em, decidido_em, decidido_por
)
SELECT
    'GOOGLE', p.id, COALESCE(NULLIF(p.nome, ''), p.email), p.email, p.avatar_url, c.nome,
    CASE
        WHEN p.aprovado = TRUE THEN 'APROVADO'
        WHEN p.consumido = TRUE THEN 'RECUSADO'
        ELSE 'PENDENTE'
    END,
    p.criado_em,
    p.expira_em,
    CASE WHEN p.aprovado = TRUE THEN p.aprovado_em
         WHEN p.consumido = TRUE THEN CURRENT_TIMESTAMP
         ELSE NULL END,
    p.aprovado_por
FROM oauth2_pending_registrations p
LEFT JOIN cargos c ON c.id = p.cargo_id
WHERE p.aprovado = TRUE
   OR p.consumido = TRUE
   OR p.expira_em > CURRENT_TIMESTAMP;

ALTER TABLE usuario_oauth
    ADD COLUMN access_token_criptografado VARCHAR(4096) NULL,
    ADD COLUMN refresh_token_criptografado VARCHAR(4096) NULL,
    ADD COLUMN access_token_expira_em TIMESTAMP NULL,
    ADD COLUMN scopes VARCHAR(1000) NULL;

ALTER TABLE oauth2_pending_registrations
    ADD COLUMN access_token_criptografado VARCHAR(4096) NULL,
    ADD COLUMN refresh_token_criptografado VARCHAR(4096) NULL,
    ADD COLUMN access_token_expira_em TIMESTAMP NULL,
    ADD COLUMN scopes VARCHAR(1000) NULL;

-- CPF continua obrigatorio na conclusao do perfil. Durante o primeiro acesso,
-- NULL permite gerenciar o usuario aprovado sem inventar um documento.
ALTER TABLE usuarios MODIFY COLUMN cpf VARCHAR(255) NULL;

-- Recupera aprovados que ainda so existem como solicitacoes Google.
-- Usuarios existentes (inclusive ativos e revogados) permanecem com seus acessos.
INSERT INTO usuarios (nome_completo, cpf, email, contato, senha_hash, situacao, cargo_id)
SELECT COALESCE(NULLIF(p.nome, ''), p.email), NULL, LOWER(TRIM(p.email)), '',
       CONCAT('GOOGLE_OAUTH_PENDING_', p.id), 'COMPLETAR_CADASTRO', p.cargo_id
FROM oauth2_pending_registrations p
WHERE p.provider = 'GOOGLE'
  AND p.aprovado = TRUE AND p.consumido = FALSE AND p.cargo_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM oauth2_pending_permissoes pp WHERE pp.pending_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM usuarios u WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(p.email)))
  AND NOT EXISTS (SELECT 1 FROM usuario_oauth o
                  WHERE o.provider = p.provider AND o.provider_user_id = p.provider_user_id)
  AND NOT EXISTS (SELECT 1 FROM oauth2_pending_registrations posterior
                  WHERE posterior.provider = 'GOOGLE' AND posterior.aprovado = TRUE
                    AND posterior.consumido = FALSE AND posterior.cargo_id IS NOT NULL
                    AND LOWER(TRIM(posterior.email)) = LOWER(TRIM(p.email))
                    AND posterior.id > p.id
                    AND EXISTS (SELECT 1 FROM oauth2_pending_permissoes pp
                                WHERE pp.pending_id = posterior.id));

INSERT INTO usuario_oauth (
    usuario_id, provider, provider_user_id, email_provider, nome_provider, avatar_url,
    vinculado_em, access_token_criptografado, refresh_token_criptografado,
    access_token_expira_em, scopes
)
SELECT u.id, p.provider, p.provider_user_id, p.email, p.nome, p.avatar_url,
       COALESCE(p.aprovado_em, CURRENT_TIMESTAMP), p.access_token_criptografado,
       p.refresh_token_criptografado, p.access_token_expira_em, p.scopes
FROM oauth2_pending_registrations p
JOIN usuarios u ON u.senha_hash = CONCAT('GOOGLE_OAUTH_PENDING_', p.id)
              AND u.situacao = 'COMPLETAR_CADASTRO' AND u.cpf IS NULL;

INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT u.id, pp.id_permissao
FROM oauth2_pending_registrations p
JOIN usuarios u ON u.senha_hash = CONCAT('GOOGLE_OAUTH_PENDING_', p.id)
              AND u.situacao = 'COMPLETAR_CADASTRO' AND u.cpf IS NULL
JOIN oauth2_pending_permissoes pp ON pp.pending_id = p.id;

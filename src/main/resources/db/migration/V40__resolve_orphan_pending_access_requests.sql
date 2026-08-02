UPDATE solicitacoes_acesso solicitacao
LEFT JOIN usuarios usuario
    ON solicitacao.origem = 'USUARIO'
   AND usuario.id = solicitacao.referencia_id
SET solicitacao.status = 'RECUSADO',
    solicitacao.decidido_em = COALESCE(solicitacao.decidido_em, CURRENT_TIMESTAMP),
    solicitacao.decidido_por = NULL
WHERE solicitacao.origem = 'USUARIO'
  AND solicitacao.status = 'PENDENTE'
  AND usuario.id IS NULL;

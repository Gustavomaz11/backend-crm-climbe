ALTER TABLE documentos
    ADD COLUMN titulo VARCHAR(255) NULL AFTER empresa_id,
    ADD COLUMN email_destinatario VARCHAR(255) NULL AFTER analista_id,
    ADD COLUMN token_upload VARCHAR(100) NULL AFTER email_destinatario,
    ADD COLUMN token_expira_em DATETIME NULL AFTER token_upload,
    ADD COLUMN data_solicitacao DATETIME NULL AFTER token_expira_em,
    ADD COLUMN data_envio DATETIME NULL AFTER data_solicitacao;

UPDATE documentos
SET titulo = tipo_documento
WHERE titulo IS NULL;

CREATE UNIQUE INDEX idx_documentos_token_upload ON documentos(token_upload);
CREATE INDEX idx_documentos_empresa_status ON documentos(empresa_id, validado);

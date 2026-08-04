ALTER TABLE revisoes_documento_versoes
    ADD COLUMN zapsign_documento_token VARCHAR(100) NULL,
    ADD COLUMN zapsign_signatario_token VARCHAR(100) NULL,
    ADD COLUMN zapsign_status VARCHAR(30) NULL,
    ADD COLUMN zapsign_criado_em DATETIME NULL,
    ADD COLUMN zapsign_assinado_em DATETIME NULL,
    ADD CONSTRAINT uk_revisao_versao_zapsign_documento UNIQUE (zapsign_documento_token);

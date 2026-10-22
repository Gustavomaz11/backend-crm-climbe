ALTER TABLE integracao_auditoria ADD COLUMN payload_requisicao MEDIUMTEXT NULL;
ALTER TABLE integracao_auditoria ADD COLUMN payload_limite_excedido BOOLEAN NOT NULL DEFAULT FALSE;

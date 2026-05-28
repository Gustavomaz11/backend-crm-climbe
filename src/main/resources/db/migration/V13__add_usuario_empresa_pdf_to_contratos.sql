ALTER TABLE contratos
    ADD COLUMN usuario_id BIGINT NULL,
    ADD COLUMN empresa_id BIGINT NULL,
    ADD COLUMN empresa_nome_fantasia VARCHAR(255) NULL,
    ADD COLUMN url_pdf VARCHAR(255) NULL;

UPDATE contratos c
    INNER JOIN propostas p ON p.id_proposta = c.proposta_id
    INNER JOIN empresas e ON e.id_empresa = p.empresa_id
SET c.usuario_id = p.usuario_id,
    c.empresa_id = p.empresa_id,
    c.empresa_nome_fantasia = e.nome_fantasia;

ALTER TABLE contratos
    MODIFY COLUMN usuario_id BIGINT NOT NULL,
    MODIFY COLUMN empresa_id BIGINT NOT NULL,
    MODIFY COLUMN empresa_nome_fantasia VARCHAR(255) NOT NULL;

ALTER TABLE contratos
    ADD CONSTRAINT fk_contrato_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_contrato_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa);

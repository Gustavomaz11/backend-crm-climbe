CREATE TABLE pessoas_clientes (
    id_pessoa BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(180) NOT NULL,
    cpf VARCHAR(14) NULL,
    email VARCHAR(180) NOT NULL,
    telefone VARCHAR(50) NOT NULL,
    cargo VARCHAR(120) NULL,
    observacoes TEXT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pessoas_clientes_cpf (cpf),
    INDEX idx_pessoas_clientes_nome (nome),
    INDEX idx_pessoas_clientes_email (email)
);

CREATE TABLE pessoa_cliente_empresas (
    pessoa_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    PRIMARY KEY (pessoa_id, empresa_id),
    CONSTRAINT fk_pessoa_cliente_empresas_pessoa
        FOREIGN KEY (pessoa_id) REFERENCES pessoas_clientes(id_pessoa)
        ON DELETE CASCADE,
    CONSTRAINT fk_pessoa_cliente_empresas_empresa
        FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa)
        ON DELETE CASCADE,
    INDEX idx_pessoa_cliente_empresas_empresa (empresa_id)
);

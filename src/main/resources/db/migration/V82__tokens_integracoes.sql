CREATE TABLE integracao_clientes (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(80) NOT NULL UNIQUE,
    secret_hash VARCHAR(64),
    nome VARCHAR(180) NOT NULL,
    cnpj VARCHAR(14) NOT NULL,
    responsavel VARCHAR(180) NOT NULL,
    email VARCHAR(254) NOT NULL,
    modulo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_por BIGINT NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    expira_em DATETIME(6) NOT NULL,
    ultimo_uso DATETIME(6),
    versao INT NOT NULL DEFAULT 1,
    ativacao_nonce VARCHAR(64),
    ativacao_expira_em DATETIME(6),
    email_status VARCHAR(20) NOT NULL,
    email_tentativas INT NOT NULL DEFAULT 0,
    email_proxima_tentativa DATETIME(6),
    email_versao INT NOT NULL DEFAULT 0,
    limite_por_minuto INT NOT NULL DEFAULT 120,
    janela_minuto BIGINT NOT NULL DEFAULT 0,
    requisicoes_janela INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_integracao_criador FOREIGN KEY (criado_por) REFERENCES usuarios(id),
    INDEX idx_integracao_email (email_proxima_tentativa)
);
CREATE TABLE integracao_cliente_empresas (
    integracao_id VARCHAR(36) NOT NULL,
    empresa_id BIGINT NOT NULL,
    PRIMARY KEY (integracao_id, empresa_id),
    CONSTRAINT fk_integracao_empresa_cliente FOREIGN KEY (integracao_id) REFERENCES integracao_clientes(id) ON DELETE CASCADE,
    CONSTRAINT fk_integracao_empresa_dados FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa)
);
CREATE TABLE integracao_cliente_metodos (
    integracao_id VARCHAR(36) NOT NULL,
    metodo VARCHAR(10) NOT NULL,
    PRIMARY KEY (integracao_id, metodo),
    CONSTRAINT fk_integracao_metodo_cliente FOREIGN KEY (integracao_id) REFERENCES integracao_clientes(id) ON DELETE CASCADE
);
CREATE TABLE integracao_cliente_operacoes (
    integracao_id VARCHAR(36) NOT NULL,
    operacao VARCHAR(100) NOT NULL,
    PRIMARY KEY (integracao_id, operacao),
    CONSTRAINT fk_integracao_operacao_cliente FOREIGN KEY (integracao_id) REFERENCES integracao_clientes(id) ON DELETE CASCADE
);
CREATE TABLE integracao_auditoria (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    integracao_id VARCHAR(36) NOT NULL,
    evento VARCHAR(100) NOT NULL,
    ator_id BIGINT,
    metodo VARCHAR(10),
    rota VARCHAR(255),
    status INT,
    criado_em DATETIME(6) NOT NULL,
    CONSTRAINT fk_integracao_auditoria_cliente FOREIGN KEY (integracao_id) REFERENCES integracao_clientes(id),
    INDEX idx_integracao_auditoria (integracao_id, id)
);
INSERT INTO permissoes (codigo, descricao)
SELECT 'INTEGRACOES_GERENCIAR', 'Gerenciar tokens, credenciais, escopos e documentação de integrações externas'
WHERE NOT EXISTS (SELECT 1 FROM permissoes WHERE codigo = 'INTEGRACOES_GERENCIAR');
INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT DISTINCT atual.id_usuario, nova.id_permissao
FROM usuario_permissoes atual
JOIN permissoes admin ON admin.id_permissao = atual.id_permissao AND admin.codigo = 'PERMITIR_ACESSO'
CROSS JOIN permissoes nova
WHERE nova.codigo = 'INTEGRACOES_GERENCIAR'
AND NOT EXISTS (SELECT 1 FROM usuario_permissoes existente WHERE existente.id_usuario = atual.id_usuario AND existente.id_permissao = nova.id_permissao);

CREATE TABLE revisoes_documento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    referencia_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    empresa_nome VARCHAR(255) NOT NULL,
    destinatario_email VARCHAR(255) NOT NULL,
    destinatario_nome VARCHAR(255) NULL,
    token VARCHAR(64) NOT NULL,
    token_expira_em DATETIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    versao_atual INT NOT NULL DEFAULT 1,
    justificativa TEXT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    respondido_em DATETIME NULL,
    email_status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    email_enviado_em DATETIME NULL,
    CONSTRAINT uk_revisao_tipo_referencia UNIQUE (tipo, referencia_id),
    CONSTRAINT uk_revisao_token UNIQUE (token),
    CONSTRAINT fk_revisao_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id_empresa),
    INDEX idx_revisao_status (status),
    INDEX idx_revisao_empresa (empresa_id)
);

CREATE TABLE revisoes_documento_versoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    revisao_id BIGINT NOT NULL,
    numero INT NOT NULL,
    arquivo_url VARCHAR(2048) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    total_paginas INT NOT NULL,
    resultado VARCHAR(30) NOT NULL,
    comentario_geral TEXT NULL,
    justificativa TEXT NULL,
    criado_por BIGINT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    respondido_em DATETIME NULL,
    CONSTRAINT uk_revisao_versao UNIQUE (revisao_id, numero),
    CONSTRAINT fk_revisao_versao_revisao FOREIGN KEY (revisao_id) REFERENCES revisoes_documento(id) ON DELETE CASCADE,
    CONSTRAINT fk_revisao_versao_usuario FOREIGN KEY (criado_por) REFERENCES usuarios(id),
    INDEX idx_revisao_versao_revisao (revisao_id)
);

CREATE TABLE revisoes_documento_anotacoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    versao_id BIGINT NOT NULL,
    pagina INT NOT NULL,
    posicao_x DECIMAL(10, 8) NOT NULL,
    posicao_y DECIMAL(10, 8) NOT NULL,
    largura DECIMAL(10, 8) NOT NULL,
    altura DECIMAL(10, 8) NOT NULL,
    cor VARCHAR(20) NOT NULL DEFAULT '#FACC15',
    comentario TEXT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_revisao_anotacao_versao FOREIGN KEY (versao_id) REFERENCES revisoes_documento_versoes(id) ON DELETE CASCADE,
    INDEX idx_revisao_anotacao_versao_pagina (versao_id, pagina)
);

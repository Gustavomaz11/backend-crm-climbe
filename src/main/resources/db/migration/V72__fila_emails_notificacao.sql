CREATE TABLE emails_notificacao_pendentes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    destinatario VARCHAR(320) NOT NULL,
    assunto VARCHAR(255) NOT NULL,
    mensagem TEXT,
    destino VARCHAR(1000),
    criado_em DATETIME(6) NOT NULL,
    proxima_tentativa DATETIME(6) NOT NULL,
    tentativas INT NOT NULL DEFAULT 0,
    INDEX idx_email_notificacao_proxima (proxima_tentativa, id)
);

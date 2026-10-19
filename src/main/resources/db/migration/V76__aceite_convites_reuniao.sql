ALTER TABLE participantes_reuniao
    ADD COLUMN resposta VARCHAR(16) NOT NULL DEFAULT 'PENDENTE',
    ADD COLUMN respondido_em DATETIME NULL,
    ADD COLUMN aceite_sincronizado_google BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

UPDATE participantes_reuniao p JOIN reunioes r ON r.id_reuniao = p.id_reuniao
SET p.resposta = 'ACEITA', p.respondido_em = NOW(), p.aceite_sincronizado_google = TRUE
WHERE p.id_usuario = r.organizador_id;

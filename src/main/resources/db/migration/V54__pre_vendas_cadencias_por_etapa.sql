ALTER TABLE pipeline_vendas_funis ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'VENDAS';
INSERT INTO pipeline_vendas_funis (codigo, nome, estrategia, posicao, ativo, tipo)
SELECT 'PRE_VENDAS', 'Pré-vendas', 'Todas as estratégias', COALESCE(MAX(posicao), 0) + 1, TRUE, 'PRE_VENDAS'
FROM pipeline_vendas_funis;
INSERT INTO pipeline_vendas_etapas (funil_id, codigo, nome, posicao, resultado, ativo, campos_obrigatorios)
SELECT id_funil, 'LISTA_LEADS', 'Lista de leads', 1, 'ABERTO', TRUE, JSON_ARRAY() FROM pipeline_vendas_funis WHERE codigo = 'PRE_VENDAS';
INSERT INTO pipeline_vendas_etapas (funil_id, codigo, nome, posicao, resultado, ativo, campos_obrigatorios)
SELECT id_funil, 'TENTATIVA_CONTATO', 'Tentativa de contato', 2, 'ABERTO', TRUE, JSON_ARRAY() FROM pipeline_vendas_funis WHERE codigo = 'PRE_VENDAS';
INSERT INTO pipeline_vendas_etapas (funil_id, codigo, nome, posicao, resultado, ativo, campos_obrigatorios)
SELECT id_funil, 'LEAD_CONECTADO', 'Lead conectado', 3, 'ABERTO', TRUE, JSON_ARRAY() FROM pipeline_vendas_funis WHERE codigo = 'PRE_VENDAS';
INSERT INTO pipeline_vendas_etapas (funil_id, codigo, nome, posicao, resultado, ativo, campos_obrigatorios)
SELECT id_funil, 'REUNIAO_MARCADA', 'Reunião marcada', 4, 'ABERTO', TRUE, JSON_ARRAY() FROM pipeline_vendas_funis WHERE codigo = 'PRE_VENDAS';
INSERT INTO pipeline_vendas_etapas (funil_id, codigo, nome, posicao, resultado, ativo, campos_obrigatorios)
SELECT id_funil, 'REUNIAO_REALIZADA', 'Reunião realizada', 5, 'ABERTO', TRUE, JSON_ARRAY() FROM pipeline_vendas_funis WHERE codigo = 'PRE_VENDAS';

ALTER TABLE pessoas_clientes MODIFY COLUMN email VARCHAR(180) NULL, MODIFY COLUMN telefone VARCHAR(50) NULL;
ALTER TABLE pipeline_vendas_negocios
    MODIFY COLUMN email VARCHAR(180) NULL, MODIFY COLUMN telefone VARCHAR(50) NULL,
    ADD COLUMN pessoa_id BIGINT NULL,
    ADD COLUMN campanha_origem_id BIGINT NULL,
    ADD COLUMN pre_venda_origem_id BIGINT NULL,
    ADD CONSTRAINT fk_negocio_pessoa FOREIGN KEY (pessoa_id) REFERENCES pessoas_clientes(id_pessoa),
    ADD CONSTRAINT fk_negocio_campanha_origem FOREIGN KEY (campanha_origem_id) REFERENCES pipeline_campanhas(id_campanha),
    ADD CONSTRAINT fk_negocio_pre_venda FOREIGN KEY (pre_venda_origem_id) REFERENCES pipeline_vendas_negocios(id_negocio),
    ADD CONSTRAINT uk_negocio_pre_venda UNIQUE (pre_venda_origem_id);

ALTER TABLE pipeline_campanhas ADD COLUMN versao INT NOT NULL DEFAULT 1;
ALTER TABLE pipeline_cadencia_etapas
    ADD COLUMN versao INT NOT NULL DEFAULT 1,
    ADD COLUMN etapa_funil_codigo VARCHAR(80) NOT NULL DEFAULT '',
    ADD COLUMN script_modelo TEXT NULL,
    DROP INDEX uk_pipeline_cadencia_ordem,
    ADD CONSTRAINT uk_pipeline_cadencia_versao_ordem UNIQUE (campanha_id, versao, ordem);
UPDATE pipeline_cadencia_etapas e JOIN pipeline_scripts s ON s.id_script = e.script_id SET e.script_modelo = s.modelo_mensagem;
ALTER TABLE pipeline_campanha_execucoes
    ADD COLUMN versao INT NOT NULL DEFAULT 1,
    ADD COLUMN etapa_funil_codigo VARCHAR(80) NOT NULL DEFAULT '',
    DROP INDEX uk_pipeline_execucao_lead,
    ADD CONSTRAINT uk_pipeline_execucao_etapa UNIQUE (campanha_id, negocio_id, etapa_funil_codigo);
ALTER TABLE pipeline_vendas_tarefas
    ADD COLUMN motivo_cancelamento VARCHAR(80) NULL,
    ADD COLUMN comentario_cancelamento VARCHAR(1000) NULL,
    ADD COLUMN cancelado_em DATETIME NULL,
    ADD COLUMN cancelado_por BIGINT NULL,
    ADD CONSTRAINT fk_tarefa_cancelado_por FOREIGN KEY (cancelado_por) REFERENCES usuarios(id);

CREATE TABLE pipeline_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(80) NOT NULL UNIQUE,
    cor VARCHAR(7) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE pipeline_negocio_tags (
    negocio_id BIGINT NOT NULL, tag_id BIGINT NOT NULL,
    PRIMARY KEY (negocio_id, tag_id),
    FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio),
    FOREIGN KEY (tag_id) REFERENCES pipeline_tags(id)
);
CREATE TABLE pipeline_campos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    tipo VARCHAR(20) NOT NULL,
    escopo VARCHAR(20) NOT NULL,
    opcoes TEXT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE pipeline_negocio_campos (
    negocio_id BIGINT NOT NULL, campo_id BIGINT NOT NULL, valor VARCHAR(4000) NOT NULL,
    PRIMARY KEY (negocio_id, campo_id),
    FOREIGN KEY (negocio_id) REFERENCES pipeline_vendas_negocios(id_negocio),
    FOREIGN KEY (campo_id) REFERENCES pipeline_campos(id)
);
INSERT INTO permissoes (descricao, codigo) VALUES
('Comercial — gerenciar tags e campos personalizados', 'COMERCIAL_CADASTROS_GERENCIAR');
INSERT INTO usuario_permissoes (id_usuario, id_permissao)
SELECT DISTINCT up.id_usuario, p.id_permissao FROM usuario_permissoes up
JOIN permissoes original ON original.id_permissao = up.id_permissao AND original.codigo = 'COMERCIAL_FUNIL_EDITAR'
JOIN permissoes p ON p.codigo = 'COMERCIAL_CADASTROS_GERENCIAR';

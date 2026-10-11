ALTER TABLE contrato_kanban_tasks ADD COLUMN arquivado_em TIMESTAMP NULL;
ALTER TABLE contrato_kanban_tasks ADD COLUMN arquivar_apos TIMESTAMP NULL;
CREATE INDEX idx_contrato_kanban_tasks_arquivamento ON contrato_kanban_tasks (arquivado_em, arquivar_apos);

ALTER TABLE pipeline_vendas_tarefas ADD COLUMN arquivado_em TIMESTAMP NULL;
ALTER TABLE pipeline_vendas_tarefas ADD COLUMN arquivar_apos TIMESTAMP NULL;
CREATE INDEX idx_pipeline_vendas_tarefas_arquivamento ON pipeline_vendas_tarefas (arquivado_em, arquivar_apos);

ALTER TABLE pipeline_vendas_negocios ADD COLUMN arquivado_em TIMESTAMP NULL;
ALTER TABLE pipeline_vendas_negocios ADD COLUMN arquivar_apos TIMESTAMP NULL;
CREATE INDEX idx_pipeline_vendas_negocios_arquivamento ON pipeline_vendas_negocios (arquivado_em, arquivar_apos);

ALTER TABLE contratos ADD COLUMN arquivado_em TIMESTAMP NULL;
ALTER TABLE contratos ADD COLUMN arquivar_apos TIMESTAMP NULL;
CREATE INDEX idx_contratos_arquivamento ON contratos (arquivado_em, arquivar_apos);

ALTER TABLE reunioes ADD COLUMN arquivado_em TIMESTAMP NULL;
ALTER TABLE reunioes ADD COLUMN arquivar_apos TIMESTAMP NULL;
CREATE INDEX idx_reunioes_arquivamento ON reunioes (arquivado_em, arquivar_apos);

UPDATE contrato_kanban_tasks
SET arquivar_apos = TIMESTAMPADD(DAY, 15, COALESCE(concluida_em, atualizado_em, criado_em))
WHERE raia_id IN (SELECT id_raia FROM contrato_kanban_raias WHERE conclui_tarefas = TRUE);

UPDATE pipeline_vendas_tarefas
SET arquivar_apos = TIMESTAMPADD(DAY, 15, COALESCE(concluido_em, atualizado_em, criado_em))
WHERE status = 'CONCLUIDA';

UPDATE pipeline_vendas_negocios
SET arquivar_apos = TIMESTAMPADD(DAY, 15, COALESCE(encerrado_em, ultima_movimentacao_em, criado_em))
WHERE resultado IN ('GANHO', 'PERDIDO');

UPDATE contratos
SET arquivar_apos = TIMESTAMPADD(DAY, 15, COALESCE(CAST(data_aprovacao AS DATETIME), CURRENT_TIMESTAMP))
WHERE etapa_preparacao = 'CONCLUIDO' OR status = 'APROVADO';

UPDATE reunioes
SET arquivar_apos = TIMESTAMPADD(DAY, 15, COALESCE(CAST(data AS DATETIME), CURRENT_TIMESTAMP))
WHERE status IN ('REALIZADA', 'REALIZADO', 'CONCLUIDA', 'CONCLUIDO');

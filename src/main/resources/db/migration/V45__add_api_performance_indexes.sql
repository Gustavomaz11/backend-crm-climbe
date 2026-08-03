CREATE INDEX idx_usuarios_situacao_nome
    ON usuarios (situacao, nome_completo);

CREATE INDEX idx_pipeline_negocio_criacao
    ON pipeline_vendas_negocios (criado_em);

CREATE INDEX idx_pipeline_negocio_resultado_criacao
    ON pipeline_vendas_negocios (resultado, criado_em);

CREATE INDEX idx_pipeline_tarefa_negocio_status_prazo
    ON pipeline_vendas_tarefas (negocio_id, prazo, status);

CREATE INDEX idx_contrato_empresa_ordem
    ON contratos (empresa_id, id_contrato);

CREATE INDEX idx_proposta_status_ordem
    ON propostas (status, id_proposta);

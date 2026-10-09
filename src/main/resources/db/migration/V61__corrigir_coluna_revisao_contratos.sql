-- Revisão é exclusiva de solicitações de ajustes feitas pelo cliente.
-- Preserva as etapas definidas manualmente fora da antiga regra de envio.
UPDATE contratos
SET etapa_preparacao = CASE
    WHEN status = 'APROVADO' THEN 'CONCLUIDO'
    WHEN EXISTS (
        SELECT 1 FROM revisoes_documento r
        WHERE r.tipo = 'CONTRATO'
          AND r.referencia_id = contratos.id_contrato
          AND r.status = 'AJUSTES_SOLICITADOS'
    ) THEN 'REVISAO'
    WHEN NULLIF(TRIM(url_pdf), '') IS NOT NULL THEN 'EM_ANDAMENTO'
    ELSE 'A_FAZER'
END
WHERE etapa_preparacao IS NULL OR etapa_preparacao = 'REVISAO';

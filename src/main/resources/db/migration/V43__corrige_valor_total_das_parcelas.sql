-- O campo propostas.valuation representa o valor total da proposta.
-- Corrige parcelas pendentes geradas antes dessa regra para servicos recorrentes.
UPDATE contrato_parcelas cp
JOIN contratos c ON c.id_contrato = cp.contrato_id
JOIN propostas p ON p.id_proposta = c.proposta_id
SET cp.valor = COALESCE(
    (
        SELECT pr.valor
        FROM proposta_reajustes pr
        WHERE pr.proposta_id = p.id_proposta
          AND pr.mes_vigencia <= cp.numero
        ORDER BY pr.mes_vigencia DESC
        LIMIT 1
    ),
    CASE
        WHEN cp.numero = CASE
            WHEN p.recorrencia_meses IS NULL OR p.recorrencia_meses = 0 THEN 24
            ELSE p.recorrencia_meses
        END
        THEN p.valuation - TRUNCATE(
            p.valuation / CASE
                WHEN p.recorrencia_meses IS NULL OR p.recorrencia_meses = 0 THEN 24
                ELSE p.recorrencia_meses
            END,
            2
        ) * (CASE
            WHEN p.recorrencia_meses IS NULL OR p.recorrencia_meses = 0 THEN 24
            ELSE p.recorrencia_meses
        END - 1)
        ELSE TRUNCATE(
            p.valuation / CASE
                WHEN p.recorrencia_meses IS NULL OR p.recorrencia_meses = 0 THEN 24
                ELSE p.recorrencia_meses
            END,
            2
        )
    END
)
WHERE cp.status = 'PENDENTE'
  AND p.valuation IS NOT NULL
  AND p.valuation > 0
  AND p.servico IN ('BPO', 'CFO', 'CONTABILIDADE', 'FINANCE_SUPPORT', 'DESENVOLVIMENTO_SOFTWARE');

-- Servicos nao recorrentes tambem usam o valor total dividido pelas parcelas.
UPDATE contrato_parcelas cp
JOIN contratos c ON c.id_contrato = cp.contrato_id
JOIN propostas p ON p.id_proposta = c.proposta_id
SET cp.valor = CASE
    WHEN cp.numero = GREATEST(COALESCE(p.quantidade_parcelas, 1), 1)
    THEN p.valuation - TRUNCATE(
        p.valuation / GREATEST(COALESCE(p.quantidade_parcelas, 1), 1),
        2
    ) * (GREATEST(COALESCE(p.quantidade_parcelas, 1), 1) - 1)
    ELSE TRUNCATE(
        p.valuation / GREATEST(COALESCE(p.quantidade_parcelas, 1), 1),
        2
    )
END
WHERE cp.status = 'PENDENTE'
  AND p.valuation IS NOT NULL
  AND p.valuation > 0
  AND p.servico IN ('VALUATION', 'CONSORCIO');

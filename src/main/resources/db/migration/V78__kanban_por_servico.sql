ALTER TABLE contrato_kanban_raias ADD COLUMN servico VARCHAR(50) NULL;

-- As tarefas anteriores permanecem no primeiro serviço, sem duplicar seu histórico.
UPDATE contrato_kanban_raias
SET servico = (
    SELECT COALESCE(
        (SELECT ps.servico FROM proposta_servicos ps
         WHERE ps.proposta_id = c.proposta_id ORDER BY ps.posicao LIMIT 1),
        c.servico, p.servico
    )
    FROM contratos c LEFT JOIN propostas p ON p.id_proposta = c.proposta_id
    WHERE c.id_contrato = contrato_kanban_raias.contrato_id
);

CREATE INDEX idx_kanban_raia_contrato_servico ON contrato_kanban_raias(contrato_id, servico, posicao);

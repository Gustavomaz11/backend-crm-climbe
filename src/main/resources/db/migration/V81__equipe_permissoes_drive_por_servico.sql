CREATE TABLE contrato_equipe_servico (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    servico VARCHAR(50) NOT NULL,
    CONSTRAINT fk_equipe_servico_contrato FOREIGN KEY (contrato_id) REFERENCES contratos(id_contrato) ON DELETE CASCADE,
    CONSTRAINT fk_equipe_servico_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uk_contrato_equipe_servico UNIQUE (contrato_id, usuario_id, servico)
);

-- Preserva a equipe antiga. Nos contratos com vários serviços, somente vínculos
-- comprovados por liderança ou atribuição de tarefas são transferidos automaticamente.
INSERT INTO contrato_equipe_servico (contrato_id, usuario_id, servico)
SELECT DISTINCT m.contrato_id, m.usuario_id, COALESCE(ps.servico, c.servico, p.servico, 'SEM_SERVICO')
FROM contrato_equipe m JOIN contratos c ON c.id_contrato = m.contrato_id
LEFT JOIN propostas p ON p.id_proposta = c.proposta_id
LEFT JOIN proposta_servicos ps ON ps.proposta_id = c.proposta_id
LEFT JOIN contrato_responsaveis_servico rs ON rs.contrato_id = c.id_contrato
    AND rs.servico = COALESCE(ps.servico, c.servico, p.servico)
WHERE (
    (SELECT COUNT(*) FROM proposta_servicos s WHERE s.proposta_id = c.proposta_id) <= 1
    OR m.usuario_id = COALESCE(rs.usuario_id, c.id_responsavel)
    OR EXISTS (SELECT 1 FROM contrato_kanban_tasks t JOIN contrato_kanban_raias r ON r.id_raia = t.raia_id
        WHERE t.contrato_id = c.id_contrato AND r.servico = ps.servico
        AND (t.id_responsavel = m.usuario_id OR EXISTS (
            SELECT 1 FROM contrato_kanban_task_responsaveis tr WHERE tr.task_id = t.id_task AND tr.usuario_id = m.usuario_id)))
);

CREATE INDEX idx_equipe_contrato_servico ON contrato_equipe_servico (contrato_id, servico);

ALTER TABLE empresa_drive_servicos ADD COLUMN permissoes_tentar_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE empresa_drive_servicos ADD COLUMN permissoes_sincronizadas_em TIMESTAMP;
ALTER TABLE empresa_drive_servicos ADD COLUMN permissoes_erro VARCHAR(1000);
CREATE INDEX idx_drive_servico_permissoes ON empresa_drive_servicos (permissoes_tentar_em);

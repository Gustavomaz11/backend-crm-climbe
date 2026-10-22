package com.climb.api.integracao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IntegracaoAuditoriaRepository extends JpaRepository<IntegracaoAuditoria, Long> {
    List<IntegracaoAuditoria> findTop100ByIntegracaoIdOrderByIdDesc(String integracaoId);
    java.util.Optional<IntegracaoAuditoria> findByIdAndIntegracaoId(Long id, String integracaoId);
}

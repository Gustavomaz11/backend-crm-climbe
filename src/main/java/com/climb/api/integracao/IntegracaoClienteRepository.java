package com.climb.api.integracao;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;

public interface IntegracaoClienteRepository extends JpaRepository<IntegracaoCliente, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IntegracaoCliente> findByClientId(String clientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IntegracaoCliente c where c.id = :id")
    Optional<IntegracaoCliente> bloquear(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IntegracaoCliente c where c.emailProximaTentativa <= :agora and c.emailStatus in ('PENDENTE', 'FALHOU') order by c.emailProximaTentativa")
    List<IntegracaoCliente> pendentes(@Param("agora") Instant agora, Pageable limite);
    List<IntegracaoCliente> findAllByOrderByCriadoEmDesc();
}

package com.climb.api.repository;

import com.climb.api.model.PipelineVendasHistorico;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineVendasHistoricoRepository extends JpaRepository<PipelineVendasHistorico, Long> {
    @EntityGraph(attributePaths = {"usuario"})
    List<PipelineVendasHistorico> findByNegocioIdNegocioOrderByCriadoEmDesc(Long negocioId);
}

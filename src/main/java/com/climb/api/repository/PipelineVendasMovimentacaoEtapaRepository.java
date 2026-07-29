package com.climb.api.repository;

import com.climb.api.model.PipelineVendasMovimentacaoEtapa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineVendasMovimentacaoEtapaRepository extends JpaRepository<PipelineVendasMovimentacaoEtapa, Long> {
    Optional<PipelineVendasMovimentacaoEtapa> findFirstByNegocioIdNegocioAndSaidaEmIsNullOrderByEntradaEmDesc(Long negocioId);

    @EntityGraph(attributePaths = {"negocio", "etapa", "etapa.funil"})
    List<PipelineVendasMovimentacaoEtapa> findByNegocioIdNegocioIn(List<Long> negocioIds);
}

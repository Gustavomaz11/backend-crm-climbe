package com.climb.api.repository;

import com.climb.api.model.PipelineVendasFunil;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineVendasFunilRepository extends JpaRepository<PipelineVendasFunil, Long> {
    @EntityGraph(attributePaths = "etapas")
    List<PipelineVendasFunil> findAllByOrderByPosicaoAsc();

    List<PipelineVendasFunil> findByAtivoTrueOrderByPosicaoAsc();

    @Override
    @EntityGraph(attributePaths = "etapas")
    Optional<PipelineVendasFunil> findById(Long id);

    Optional<PipelineVendasFunil> findTopByOrderByPosicaoDesc();
}

package com.climb.api.repository;

import com.climb.api.model.PipelineCampanha;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineCampanhaRepository extends JpaRepository<PipelineCampanha, Long> {
    @EntityGraph(attributePaths = {"leads", "participantes", "scripts", "diasExecucao", "etapas", "etapas.script", "criadoPor"})
    List<PipelineCampanha> findAllByOrderByCriadoEmDesc();

    @Override
    @EntityGraph(attributePaths = {"leads", "participantes", "scripts", "diasExecucao", "etapas", "etapas.script", "criadoPor"})
    Optional<PipelineCampanha> findById(Long id);
}

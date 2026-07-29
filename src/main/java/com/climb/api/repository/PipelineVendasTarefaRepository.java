package com.climb.api.repository;

import com.climb.api.model.PipelineVendasTarefa;
import com.climb.api.model.enums.PipelineTarefaStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineVendasTarefaRepository extends JpaRepository<PipelineVendasTarefa, Long> {
    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    List<PipelineVendasTarefa> findAllByOrderByPrazoAscCriadoEmDesc();

    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    List<PipelineVendasTarefa> findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(Long negocioId);

    @Override
    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    Optional<PipelineVendasTarefa> findById(Long id);

    long countByScriptIdScript(Long scriptId);
    long countByScriptIdScriptAndStatus(Long scriptId, PipelineTarefaStatus status);
}

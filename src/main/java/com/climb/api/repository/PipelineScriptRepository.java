package com.climb.api.repository;

import com.climb.api.model.PipelineScript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineScriptRepository extends JpaRepository<PipelineScript, Long> {
    List<PipelineScript> findAllByOrderByNomeAsc();
    List<PipelineScript> findByAtivoTrueOrderByNomeAsc();
    boolean existsByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCaseAndIdScriptNot(String nome, Long id);
}

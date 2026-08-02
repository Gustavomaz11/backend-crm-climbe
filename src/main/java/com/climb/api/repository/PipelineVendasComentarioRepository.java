package com.climb.api.repository;

import com.climb.api.model.PipelineVendasComentario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineVendasComentarioRepository extends JpaRepository<PipelineVendasComentario, Long> {
    @EntityGraph(attributePaths = {"autor", "comentarioPai"})
    List<PipelineVendasComentario> findByNegocioIdNegocioOrderByCriadoEmDesc(Long negocioId);
}

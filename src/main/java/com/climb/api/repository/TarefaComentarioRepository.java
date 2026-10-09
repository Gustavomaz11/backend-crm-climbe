package com.climb.api.repository;

import com.climb.api.model.TarefaComentario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TarefaComentarioRepository extends JpaRepository<TarefaComentario, Long> {
    @EntityGraph(attributePaths = {"autor", "comentarioPai"})
    List<TarefaComentario> findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(Long taskId);
    @EntityGraph(attributePaths = {"autor", "comentarioPai"})
    List<TarefaComentario> findByPipelineTarefa_IdTarefaOrderByCriadoEmAscIdAsc(Long tarefaId);
}

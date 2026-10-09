package com.climb.api.repository;

import com.climb.api.model.TarefaAnexo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TarefaAnexoRepository extends JpaRepository<TarefaAnexo, Long> {
    @EntityGraph(attributePaths = {"autor", "comentario", "pasta"})
    List<TarefaAnexo> findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(Long taskId);
    @EntityGraph(attributePaths = {"autor", "comentario", "pasta"})
    List<TarefaAnexo> findByPipelineTarefa_IdTarefaOrderByCriadoEmAscIdAsc(Long tarefaId);
}

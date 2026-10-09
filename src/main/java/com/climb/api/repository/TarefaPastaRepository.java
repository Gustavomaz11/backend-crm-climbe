package com.climb.api.repository;

import com.climb.api.model.TarefaPasta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TarefaPastaRepository extends JpaRepository<TarefaPasta, Long> {
    @EntityGraph(attributePaths = {"autor", "pastaPai"})
    List<TarefaPasta> findByContratoTask_IdTaskOrderByNomeAscIdAsc(Long taskId);
    @EntityGraph(attributePaths = {"autor", "pastaPai"})
    List<TarefaPasta> findByPipelineTarefa_IdTarefaOrderByNomeAscIdAsc(Long tarefaId);
}

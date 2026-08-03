package com.climb.api.repository;

import com.climb.api.model.ContratoKanbanSubtarefa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContratoKanbanSubtarefaRepository extends JpaRepository<ContratoKanbanSubtarefa, Long> {

    List<ContratoKanbanSubtarefa> findByTask_Contrato_IdContratoOrderByTask_IdTaskAscPosicaoAscIdSubtarefaAsc(Long contratoId);

    List<ContratoKanbanSubtarefa> findByTask_IdTaskInOrderByTask_IdTaskAscPosicaoAscIdSubtarefaAsc(Set<Long> taskIds);

    List<ContratoKanbanSubtarefa> findByTask_IdTaskOrderByPosicaoAscIdSubtarefaAsc(Long taskId);

    Optional<ContratoKanbanSubtarefa> findByIdSubtarefaAndTask_IdTaskAndTask_Contrato_IdContrato(
            Long idSubtarefa,
            Long taskId,
            Long contratoId
    );
}

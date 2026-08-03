package com.climb.api.repository;

import com.climb.api.model.ContratoKanbanTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContratoKanbanTaskRepository extends JpaRepository<ContratoKanbanTask, Long> {

    List<ContratoKanbanTask> findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(Long contratoId);

    List<ContratoKanbanTask> findByContrato_IdContratoAndResponsavel_IdOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(
            Long contratoId,
            Long responsavelId);

    List<ContratoKanbanTask> findByContrato_IdContratoAndResponsavel_IdInOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(
            Long contratoId,
            Set<Long> responsavelIds);

    Optional<ContratoKanbanTask> findByIdTaskAndContrato_IdContrato(Long idTask, Long contratoId);

    boolean existsByContrato_IdContratoAndResponsavel_Id(Long contratoId, Long responsavelId);

    @Query("""
            select distinct task.responsavel.id
            from ContratoKanbanTask task
            where task.contrato.idContrato = :contratoId
              and task.responsavel.id in :responsavelIds
            """)
    Set<Long> findResponsavelIdsComTasks(
            @Param("contratoId") Long contratoId,
            @Param("responsavelIds") Set<Long> responsavelIds);
}

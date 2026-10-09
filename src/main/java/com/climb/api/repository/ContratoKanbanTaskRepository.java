package com.climb.api.repository;

import com.climb.api.model.ContratoKanbanTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContratoKanbanTaskRepository extends JpaRepository<ContratoKanbanTask, Long> {
    @Query("""
            select count(task) > 0 from ContratoKanbanTask task join task.apoios apoio
            where task.contrato.idContrato = :contratoId and task.raia.concluiTarefas = false
              and apoio.id = :usuarioId
              and (task.responsavel.id = :usuarioId or apoio member of task.responsaveis)
            """)
    boolean existeApoioPendente(@Param("contratoId") Long contratoId, @Param("usuarioId") Long usuarioId);

    @Query("""
            select distinct task from ContratoKanbanTask task join fetch task.apoios
            where task.contrato.idContrato = :contratoId and task.raia.concluiTarefas = false
            """)
    List<ContratoKanbanTask> buscarApoiosPendentes(@Param("contratoId") Long contratoId);

    List<ContratoKanbanTask> findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(Long contratoId);

    List<ContratoKanbanTask> findByContrato_IdContratoAndResponsavel_IdOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(
            Long contratoId,
            Long responsavelId);

    @Query("""
            select distinct task from ContratoKanbanTask task left join task.responsaveis usuario
            where task.contrato.idContrato = :contratoId
              and (task.responsavel.id in :responsavelIds or usuario.id in :responsavelIds)
            order by task.raia.posicao, task.posicao, task.idTask
            """)
    List<ContratoKanbanTask> findByContrato_IdContratoAndResponsavel_IdInOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(
            Long contratoId,
            Set<Long> responsavelIds);

    Optional<ContratoKanbanTask> findByIdTaskAndContrato_IdContrato(Long idTask, Long contratoId);

    boolean existsByContrato_IdContratoAndResponsavel_Id(Long contratoId, Long responsavelId);

    @Query("""
            select distinct usuario.id
            from ContratoKanbanTask task
            join task.responsaveis usuario
            where task.contrato.idContrato = :contratoId
              and usuario.id in :responsavelIds
            """)
    Set<Long> findResponsavelIdsComTasks(
            @Param("contratoId") Long contratoId,
            @Param("responsavelIds") Set<Long> responsavelIds);
}

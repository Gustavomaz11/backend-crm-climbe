package com.climb.api.repository;

import com.climb.api.model.ContratoKanbanTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContratoKanbanTaskRepository extends JpaRepository<ContratoKanbanTask, Long> {
    String SERVICO_ATIVO = """
            not exists (
                select encerramento.encerradoEm
                from Contrato fechado join fechado.encerramentosPorServico encerramento
                left join fechado.proposta propostaFechada
                left join propostaFechada.servicos primeiroServico on index(primeiroServico) = 0
                where fechado = task.contrato and key(encerramento) = coalesce(
                    cast(task.raia.servico as string), cast(primeiroServico.servico as string),
                    cast(fechado.servico as string), cast(propostaFechada.servico as string), 'SEM_SERVICO')
            )
            """;
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ContratoKanbanTask item set item.arquivadoEm = :agora " +
            "where item.arquivadoEm is null and item.arquivarApos <= :agora and item.raia.concluiTarefas = true")
    int arquivarVencidos(@org.springframework.data.repository.query.Param("agora") java.time.LocalDateTime agora);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "contrato", "contrato.responsavel", "contrato.proposta", "contrato.proposta.servicos", "responsavel", "responsaveis"
    })
    @Query("""
            select distinct task from ContratoKanbanTask task
            where task.dataFim is not null and task.dataFim <= :limite
              and task.raia.concluiTarefas = false and task.contrato.status = 'APROVADO'
            and
            """ + SERVICO_ATIVO + " order by task.dataFim, task.idTask")
    List<ContratoKanbanTask> buscarAbertasComPrazoAte(@Param("limite") java.time.LocalDate limite);

    @Query("""
            select count(task) > 0 from ContratoKanbanTask task join task.apoios apoio
            where task.contrato.idContrato = :contratoId and task.raia.concluiTarefas = false
              and apoio.id = :usuarioId
              and (task.responsavel.id = :usuarioId or apoio member of task.responsaveis)
              and
            """ + SERVICO_ATIVO)
    boolean existeApoioPendente(@Param("contratoId") Long contratoId, @Param("usuarioId") Long usuarioId);

    @Query("""
            select distinct task from ContratoKanbanTask task join fetch task.apoios
            where task.contrato.idContrato = :contratoId and task.raia.concluiTarefas = false
              and
            """ + SERVICO_ATIVO)
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

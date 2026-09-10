package com.climb.api.repository;

import com.climb.api.model.PipelineVendasTarefa;
import com.climb.api.model.enums.PipelineTarefaStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

public interface PipelineVendasTarefaRepository extends JpaRepository<PipelineVendasTarefa, Long> {
    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    List<PipelineVendasTarefa> findAllByOrderByPrazoAscCriadoEmDesc();

    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    List<PipelineVendasTarefa> findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(Long negocioId);

    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    List<PipelineVendasTarefa> findByNegocioIdNegocioAndResponsavel_IdOrderByPrazoAscCriadoEmDesc(
            Long negocioId,
            Long responsavelId);

    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    @Query("""
            select distinct tarefa
            from PipelineVendasTarefa tarefa
            where (:negocioId is null or tarefa.negocio.idNegocio = :negocioId)
              and (:funilId is null or tarefa.negocio.funil.idFunil = :funilId)
              and (:todosResponsaveis = true or tarefa.responsavel.id in :responsavelIds)
              and (:tipo is null or lower(trim(tarefa.tipo)) = lower(:tipo))
              and (
                    :todas = true
                    or (:concluidas = true
                        and tarefa.status = com.climb.api.model.enums.PipelineTarefaStatus.CONCLUIDA)
                    or (:doDia = true
                        and tarefa.status not in (com.climb.api.model.enums.PipelineTarefaStatus.CONCLUIDA,
                                                  com.climb.api.model.enums.PipelineTarefaStatus.CANCELADA)
                        and (tarefa.prazo = :hoje or tarefa.dataInicio = :hoje))
                    or (:atrasadas = true
                        and tarefa.status not in (com.climb.api.model.enums.PipelineTarefaStatus.CONCLUIDA,
                                                  com.climb.api.model.enums.PipelineTarefaStatus.CANCELADA)
                        and tarefa.prazo < :hoje)
                    or (:futuras = true
                        and tarefa.status not in (com.climb.api.model.enums.PipelineTarefaStatus.CONCLUIDA,
                                                  com.climb.api.model.enums.PipelineTarefaStatus.CANCELADA)
                        and coalesce(tarefa.prazo, tarefa.dataInicio) > :hoje)
              )
            order by tarefa.prazo asc, tarefa.criadoEm desc
            """)
    List<PipelineVendasTarefa> findFiltradas(
            @Param("todas") boolean todas,
            @Param("doDia") boolean doDia,
            @Param("atrasadas") boolean atrasadas,
            @Param("futuras") boolean futuras,
            @Param("concluidas") boolean concluidas,
            @Param("hoje") LocalDate hoje,
            @Param("todosResponsaveis") boolean todosResponsaveis,
            @Param("responsavelIds") Set<Long> responsavelIds,
            @Param("negocioId") Long negocioId,
            @Param("funilId") Long funilId,
            @Param("tipo") String tipo);

    @Override
    @EntityGraph(attributePaths = {"negocio", "responsavel", "subtarefas"})
    Optional<PipelineVendasTarefa> findById(Long id);

    long countByScriptIdScript(Long scriptId);
    long countByScriptIdScriptAndStatus(Long scriptId, PipelineTarefaStatus status);

    long countByNegocioIdNegocioInAndPrazoBeforeAndStatusNotIn(
            List<Long> negocioIds,
            LocalDate prazo,
            List<PipelineTarefaStatus> status);

    @EntityGraph(attributePaths = {
            "negocio", "negocio.funil", "negocio.servicosInteresse", "responsavel", "responsavel.cargo"
    })
    @Query("""
            select distinct tarefa
            from PipelineVendasTarefa tarefa
            where tarefa.prazo is not null
              and tarefa.prazo <= :limite
              and tarefa.status not in (
                    com.climb.api.model.enums.PipelineTarefaStatus.CONCLUIDA,
                    com.climb.api.model.enums.PipelineTarefaStatus.CANCELADA
              )
            order by tarefa.responsavel.id, tarefa.prazo, tarefa.idTarefa
            """)
    List<PipelineVendasTarefa> findAbertasComPrazoAte(@Param("limite") LocalDate limite);
    @EntityGraph(attributePaths = {"negocio", "negocio.campanhaOrigem", "campanha", "responsavel"})
    @Query("""
        select tarefa from PipelineVendasTarefa tarefa
        where tarefa.status = com.climb.api.model.enums.PipelineTarefaStatus.CANCELADA
        and (:inicio is null or tarefa.canceladoEm >= :inicio)
        and (:fim is null or tarefa.canceladoEm < :fim)
        and (:campanhaId is null or tarefa.campanha.idCampanha = :campanhaId or tarefa.negocio.campanhaOrigem.idCampanha = :campanhaId)
        and (:responsavelId is null or tarefa.responsavel.id = :responsavelId)
        order by tarefa.canceladoEm desc
        """)
    List<PipelineVendasTarefa> findCancelamentos(java.time.LocalDateTime inicio, java.time.LocalDateTime fim, Long campanhaId, Long responsavelId);
}

package com.climb.api.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.climb.api.model.Contrato;

public interface ContratoRepository extends JpaRepository<Contrato, Long> {
    String ACESSO_KANBAN = """
            where contrato.status = 'APROVADO' and (
                :verTodas = true or contrato.responsavel.id = :usuarioId
                or (contrato.equipeConfigurada = true and (
                    exists (select membro.id from ContratoEquipeMembro membro
                            where membro.contrato = contrato and membro.usuario.id = :usuarioId)
                    or exists (select tarefa.idTask from ContratoKanbanTask tarefa join tarefa.apoios apoio
                               where tarefa.contrato = contrato and tarefa.raia.concluiTarefas = false
                                 and apoio.id = :usuarioId
                                 and (tarefa.responsavel.id = :usuarioId or apoio member of tarefa.responsaveis))
                ))
            )
            """;

    @org.springframework.data.jpa.repository.Query("select contrato from Contrato contrato " + ACESSO_KANBAN)
    List<Contrato> buscarVisiveisParaKanban(@org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
            @org.springframework.data.repository.query.Param("verTodas") boolean verTodas);

    @org.springframework.data.jpa.repository.Query("select count(contrato) > 0 from Contrato contrato " + ACESSO_KANBAN)
    boolean existeVisivelParaKanban(@org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
            @org.springframework.data.repository.query.Param("verTodas") boolean verTodas);

    @org.springframework.data.jpa.repository.Query("""
            select contrato.idContrato as id, contrato.empresaNomeFantasia as empresaNome,
                   contrato.urlPdf as urlPdf, contrato.status as status,
                   contrato.equipeConfigurada as equipeConfigurada,
                   coalesce(contrato.servico, proposta.servico) as servico,
                   servicoProposta.servico as servicoDaProposta
            from Contrato contrato left join contrato.proposta proposta
                 left join proposta.servicos servicoProposta
            """ + ACESSO_KANBAN + " order by contrato.idContrato, index(servicoProposta)")
    List<ContratoKanbanResumoProjection> buscarResumosVisiveisParaKanban(
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
            @org.springframework.data.repository.query.Param("verTodas") boolean verTodas);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Contrato item set item.arquivadoEm = :agora " +
            "where item.arquivadoEm is null and item.arquivarApos <= :agora and (item.etapaPreparacao = com.climb.api.model.enums.ContratoPreparacaoEtapa.CONCLUIDO or item.status = 'APROVADO')")
    int arquivarVencidos(@org.springframework.data.repository.query.Param("agora") java.time.LocalDateTime agora);


    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select contrato from Contrato contrato where contrato.idContrato = :id")
    java.util.Optional<Contrato> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Contrato> findByEmpresa_IdEmpresaOrderByIdContratoDesc(Long empresaId);

    List<Contrato> findByStatus(String status);

    List<Contrato> findByDataFimBetween(LocalDate inicio, LocalDate fim);

    boolean existsByProposta_IdProposta(Long propostaId);

    java.util.Optional<Contrato> findByProposta_IdProposta(Long propostaId);

    boolean existsByProposta_IdPropostaAndIdContratoNot(Long propostaId, Long idContrato);

}

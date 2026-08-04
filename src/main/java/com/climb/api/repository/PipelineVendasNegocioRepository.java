package com.climb.api.repository;

import com.climb.api.model.PipelineVendasNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.climb.api.model.enums.PipelineVendasResultado;
import java.time.LocalDateTime;
import java.util.List;

public interface PipelineVendasNegocioRepository extends JpaRepository<PipelineVendasNegocio, Long> {
    @EntityGraph(attributePaths = {"funil", "etapa", "responsavel", "empresa", "contrato", "motivoPerda", "servicosInteresse"})
    List<PipelineVendasNegocio> findByFunilIdFunilOrderByEtapaPosicaoAscUltimaMovimentacaoEmDesc(Long funilId);
    long countByEtapaIdEtapa(Long etapaId);

    @EntityGraph(attributePaths = {"funil", "etapa", "responsavel", "empresa", "contrato", "motivoPerda", "servicosInteresse"})
    List<PipelineVendasNegocio> findAllByOrderByCriadoEmDesc();

    @EntityGraph(attributePaths = {"funil", "etapa", "responsavel", "servicosInteresse"})
    List<PipelineVendasNegocio> findByResultadoOrderByCriadoEmDesc(PipelineVendasResultado resultado);

    @EntityGraph(attributePaths = {"funil", "etapa", "responsavel", "motivoPerda", "servicosInteresse"})
    @Query("""
            select negocio
            from PipelineVendasNegocio negocio
            where (:inicio is null or negocio.criadoEm >= :inicio)
              and (:fimExclusivo is null or negocio.criadoEm < :fimExclusivo)
              and (:responsavelId is null or negocio.responsavel.id = :responsavelId)
              and (:funilId is null or negocio.funil.idFunil = :funilId)
              and (:empresaId is null or negocio.empresa.idEmpresa = :empresaId)
              and (:resultado is null or negocio.resultado = :resultado)
              and (:estrategia is null or lower(trim(negocio.estrategiaComercial)) = lower(:estrategia))
              and (:servico is null or :servico member of negocio.servicosInteresse)
              and (:origem is null or lower(trim(negocio.origemNegocio)) = lower(:origem))
            order by negocio.criadoEm desc
            """)
    List<PipelineVendasNegocio> findDashboardNegocios(
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo,
            @Param("responsavelId") Long responsavelId,
            @Param("funilId") Long funilId,
            @Param("empresaId") Long empresaId,
            @Param("resultado") PipelineVendasResultado resultado,
            @Param("estrategia") String estrategia,
            @Param("servico") String servico,
            @Param("origem") String origem);

    @Query("""
            select distinct negocio.estrategiaComercial as estrategia,
                   servico as servico,
                   negocio.origemNegocio as origem
            from PipelineVendasNegocio negocio
            join negocio.servicosInteresse servico
            """)
    List<PipelineFiltroOptionProjection> findDashboardFilterOptions();
}

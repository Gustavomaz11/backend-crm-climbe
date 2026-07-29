package com.climb.api.repository;

import com.climb.api.model.PipelineVendasNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface PipelineVendasNegocioRepository extends JpaRepository<PipelineVendasNegocio, Long> {
    List<PipelineVendasNegocio> findByFunilIdFunilOrderByEtapaPosicaoAscUltimaMovimentacaoEmDesc(Long funilId);
    long countByEtapaIdEtapa(Long etapaId);

    @EntityGraph(attributePaths = {"funil", "etapa", "responsavel", "empresa", "contrato", "motivoPerda"})
    List<PipelineVendasNegocio> findAllByOrderByCriadoEmDesc();
}

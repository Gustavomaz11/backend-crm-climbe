package com.climb.api.repository;

import com.climb.api.model.PipelineVendasEtapa;
import com.climb.api.model.enums.PipelineVendasResultado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineVendasEtapaRepository extends JpaRepository<PipelineVendasEtapa, Long> {
    List<PipelineVendasEtapa> findByFunilIdFunilAndAtivoTrueOrderByPosicaoAsc(Long funilId);
    Optional<PipelineVendasEtapa> findFirstByFunilIdFunilAndResultadoAndAtivoTrueOrderByPosicaoAsc(
            Long funilId,
            PipelineVendasResultado resultado
    );
}

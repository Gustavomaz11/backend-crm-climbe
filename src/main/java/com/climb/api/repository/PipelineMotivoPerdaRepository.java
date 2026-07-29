package com.climb.api.repository;

import com.climb.api.model.PipelineMotivoPerda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineMotivoPerdaRepository extends JpaRepository<PipelineMotivoPerda, Long> {
    List<PipelineMotivoPerda> findAllByOrderByPosicaoAscNomeAsc();
    List<PipelineMotivoPerda> findByAtivoTrueOrderByPosicaoAscNomeAsc();
    boolean existsByNomeIgnoreCaseAndIdMotivoNot(String nome, Long idMotivo);
    boolean existsByNomeIgnoreCase(String nome);
}

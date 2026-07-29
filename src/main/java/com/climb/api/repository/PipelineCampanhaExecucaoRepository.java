package com.climb.api.repository;

import com.climb.api.model.PipelineCampanhaExecucao;
import com.climb.api.model.enums.PipelineExecucaoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PipelineCampanhaExecucaoRepository extends JpaRepository<PipelineCampanhaExecucao, Long> {
    Optional<PipelineCampanhaExecucao> findByCampanhaIdCampanhaAndNegocioIdNegocio(Long campanhaId, Long negocioId);

    List<PipelineCampanhaExecucao> findByCampanhaIdCampanha(Long campanhaId);

    long countByCampanhaIdCampanhaAndStatus(Long campanhaId, PipelineExecucaoStatus status);

    Optional<PipelineCampanhaExecucao> findByTarefaAtualIdTarefa(Long tarefaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"campanha", "campanha.etapas", "campanha.etapas.script", "campanha.diasExecucao", "negocio", "participante", "tarefaAtual"})
    @Query("select execucao from PipelineCampanhaExecucao execucao where execucao.status = com.climb.api.model.enums.PipelineExecucaoStatus.ATIVA")
    List<PipelineCampanhaExecucao> buscarAtivasParaProcessamento();
}

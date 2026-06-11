package com.climb.api.repository;

import com.climb.api.model.HistoricoAprovacaoProposta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoAprovacaoPropostaRepository extends JpaRepository<HistoricoAprovacaoProposta, Long> {
	List<HistoricoAprovacaoProposta> findByPropostaIdOrderByDataAlteracaoDesc(Long propostaId);
}
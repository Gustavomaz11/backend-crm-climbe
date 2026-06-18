package com.climb.api.repository;

import com.climb.api.model.HistoricoAprovacaoContrato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoAprovacaoContratoRepository extends JpaRepository<HistoricoAprovacaoContrato, Long> {
    List<HistoricoAprovacaoContrato> findByContratoIdOrderByDataAlteracaoDesc(Long contratoId);
}

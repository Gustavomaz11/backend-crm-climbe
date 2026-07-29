package com.climb.api.repository;

import com.climb.api.model.RevisaoDocumentoAnotacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RevisaoDocumentoAnotacaoRepository extends JpaRepository<RevisaoDocumentoAnotacao, Long> {
    List<RevisaoDocumentoAnotacao> findByVersaoIdOrderByPaginaAscIdAsc(Long versaoId);
}

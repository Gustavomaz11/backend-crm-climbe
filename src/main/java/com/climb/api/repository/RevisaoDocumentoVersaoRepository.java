package com.climb.api.repository;

import com.climb.api.model.RevisaoDocumentoVersao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RevisaoDocumentoVersaoRepository extends JpaRepository<RevisaoDocumentoVersao, Long> {
    Optional<RevisaoDocumentoVersao> findByRevisaoIdAndNumero(Long revisaoId, int numero);
    List<RevisaoDocumentoVersao> findByRevisaoIdOrderByNumeroDesc(Long revisaoId);
}

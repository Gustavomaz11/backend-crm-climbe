package com.climb.api.repository;

import com.climb.api.model.RevisaoDocumento;
import com.climb.api.model.enums.RevisaoDocumentoTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RevisaoDocumentoRepository extends JpaRepository<RevisaoDocumento, Long> {
    Optional<RevisaoDocumento> findByToken(String token);
    Optional<RevisaoDocumento> findByTipoAndReferenciaId(RevisaoDocumentoTipo tipo, Long referenciaId);
}

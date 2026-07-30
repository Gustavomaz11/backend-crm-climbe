package com.climb.api.repository;

import com.climb.api.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByEmpresa_IdEmpresa(Long empresaId);

    Optional<Documento> findByTokenUpload(String tokenUpload);

    List<Documento> findByLote_IdOrderByIdDocumentoAsc(Long loteId);

}

package com.climb.api.repository;

import com.climb.api.model.DocumentoLote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentoLoteRepository extends JpaRepository<DocumentoLote, Long> {
    Optional<DocumentoLote> findByTokenUpload(String tokenUpload);
}

package com.climb.api.repository;

import com.climb.api.model.GrupoCargo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrupoCargoRepository extends JpaRepository<GrupoCargo, Long> {

    List<GrupoCargo> findAllByAtivoTrueOrderByNomeAsc();

    Optional<GrupoCargo> findByIdAndAtivoTrue(Long id);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}

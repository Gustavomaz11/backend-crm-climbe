package com.climb.api.repository;

import com.climb.api.model.GrupoPermissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrupoPermissaoRepository extends JpaRepository<GrupoPermissao, Long> {
    List<GrupoPermissao> findAllByAtivoTrueOrderByNomeAsc();
    boolean existsByNomeIgnoreCaseAndAtivoTrue(String nome);
}

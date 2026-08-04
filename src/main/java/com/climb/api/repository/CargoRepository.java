package com.climb.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.climb.api.model.Cargo;

public interface CargoRepository extends JpaRepository<Cargo, Long> {

    Optional<Cargo> findByNome(String nome);

    List<Cargo> findAllByAtivoTrueOrderByNomeAsc();

    List<Cargo> findAllByAtivoTrueOrderByOrdemHierarquiaAscNomeAsc();

    List<Cargo> findByCargoSuperiorIdAndAtivoTrueOrderByOrdemHierarquiaAscNomeAsc(Long cargoSuperiorId);

    Optional<Cargo> findByIdAndAtivoTrue(Long id);

    List<Cargo> findAllByGrupoIdAndAtivoTrue(Long grupoId);

}

package com.climb.api.repository;

import com.climb.api.model.ContratoKanbanRaia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratoKanbanRaiaRepository extends JpaRepository<ContratoKanbanRaia, Long> {

    List<ContratoKanbanRaia> findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(Long contratoId);

    Optional<ContratoKanbanRaia> findByIdRaiaAndContrato_IdContrato(Long idRaia, Long contratoId);
}

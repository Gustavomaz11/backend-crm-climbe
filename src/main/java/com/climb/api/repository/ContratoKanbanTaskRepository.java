package com.climb.api.repository;

import com.climb.api.model.ContratoKanbanTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratoKanbanTaskRepository extends JpaRepository<ContratoKanbanTask, Long> {

    List<ContratoKanbanTask> findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(Long contratoId);

    Optional<ContratoKanbanTask> findByIdTaskAndContrato_IdContrato(Long idTask, Long contratoId);

    boolean existsByContrato_IdContratoAndResponsavel_Id(Long contratoId, Long responsavelId);
}

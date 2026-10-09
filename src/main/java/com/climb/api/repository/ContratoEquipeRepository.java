package com.climb.api.repository;

import com.climb.api.model.ContratoEquipeMembro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContratoEquipeRepository extends JpaRepository<ContratoEquipeMembro, Long> {
    boolean existsByContrato_IdContratoAndUsuario_Id(Long contratoId, Long usuarioId);
    List<ContratoEquipeMembro> findByContrato_IdContrato(Long contratoId);
}

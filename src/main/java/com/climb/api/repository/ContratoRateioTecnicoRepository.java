package com.climb.api.repository;

import com.climb.api.model.ContratoRateioTecnicoParticipante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ContratoRateioTecnicoRepository extends JpaRepository<ContratoRateioTecnicoParticipante, Long> {
    List<ContratoRateioTecnicoParticipante> findByContrato_IdContratoAndCompetencia(Long contratoId, LocalDate competencia);
    List<ContratoRateioTecnicoParticipante> findByContrato_IdContrato(Long contratoId);
}

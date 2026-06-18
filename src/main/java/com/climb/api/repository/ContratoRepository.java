package com.climb.api.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.climb.api.model.Contrato;

public interface ContratoRepository extends JpaRepository<Contrato, Long> {

    List<Contrato> findByStatus(String status);

    List<Contrato> findByDataFimBetween(LocalDate inicio, LocalDate fim);

    boolean existsByProposta_IdProposta(Long propostaId);

    boolean existsByProposta_IdPropostaAndIdContratoNot(Long propostaId, Long idContrato);

}

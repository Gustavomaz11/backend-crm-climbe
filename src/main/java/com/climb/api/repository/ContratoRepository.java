package com.climb.api.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.climb.api.model.Contrato;

public interface ContratoRepository extends JpaRepository<Contrato, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Contrato item set item.arquivadoEm = :agora " +
            "where item.arquivadoEm is null and item.arquivarApos <= :agora and (item.etapaPreparacao = com.climb.api.model.enums.ContratoPreparacaoEtapa.CONCLUIDO or item.status = 'APROVADO')")
    int arquivarVencidos(@org.springframework.data.repository.query.Param("agora") java.time.LocalDateTime agora);


    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select contrato from Contrato contrato where contrato.idContrato = :id")
    java.util.Optional<Contrato> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Contrato> findByEmpresa_IdEmpresaOrderByIdContratoDesc(Long empresaId);

    List<Contrato> findByStatus(String status);

    List<Contrato> findByDataFimBetween(LocalDate inicio, LocalDate fim);

    boolean existsByProposta_IdProposta(Long propostaId);

    java.util.Optional<Contrato> findByProposta_IdProposta(Long propostaId);

    boolean existsByProposta_IdPropostaAndIdContratoNot(Long propostaId, Long idContrato);

}

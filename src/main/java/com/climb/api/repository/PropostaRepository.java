package com.climb.api.repository;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.climb.api.model.Proposta;
import com.climb.api.model.enums.PropostaStatus;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {
    org.springframework.data.domain.Page<Proposta> findByEmpresa_IdEmpresaIn(java.util.Collection<Long> ids, org.springframework.data.domain.Pageable pagina);

    List<Proposta> findByStatus(PropostaStatus status);

    List<Proposta> findByEmpresaIdEmpresaOrderByDataCriacaoDescIdPropostaDesc(Long empresaId);

    List<Proposta> findByNegocioIdNegocioOrderByDataCriacaoDescIdPropostaDesc(Long negocioId);

    boolean existsByNegocioIdNegocio(Long negocioId);

    @Query("""
            select distinct proposta.negocio.idNegocio
              from Proposta proposta
             where proposta.negocio.idNegocio in :negocioIds
            """)
    List<Long> findNegocioIdsComProposta(@Param("negocioIds") Collection<Long> negocioIds);

}

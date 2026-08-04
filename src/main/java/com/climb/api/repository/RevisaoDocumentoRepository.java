package com.climb.api.repository;

import com.climb.api.model.RevisaoDocumento;
import com.climb.api.model.enums.RevisaoDocumentoTipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RevisaoDocumentoRepository extends JpaRepository<RevisaoDocumento, Long> {
    Optional<RevisaoDocumento> findByToken(String token);
    Optional<RevisaoDocumento> findByTipoAndReferenciaId(RevisaoDocumentoTipo tipo, Long referenciaId);
    List<RevisaoDocumento> findByTipoAndReferenciaIdIn(RevisaoDocumentoTipo tipo, Collection<Long> referenciaIds);

    @Query(value = """
            select distinct proposta.negocio_id
              from propostas proposta
              join revisoes_documento revisao
                on revisao.referencia_id = proposta.id_proposta
               and revisao.tipo = 'PROPOSTA'
             where proposta.negocio_id in (:negocioIds)
               and revisao.status = 'AJUSTES_SOLICITADOS'
            """, nativeQuery = true)
    List<Long> findNegocioIdsComAjustesSolicitados(@Param("negocioIds") Collection<Long> negocioIds);
}

package com.climb.api.repository;

import com.climb.api.model.RevisaoDocumentoAnotacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RevisaoDocumentoAnotacaoRepository extends JpaRepository<RevisaoDocumentoAnotacao, Long> {
    List<RevisaoDocumentoAnotacao> findByVersaoIdOrderByPaginaAscIdAsc(Long versaoId);

    @Query("""
            select anotacao
            from RevisaoDocumentoAnotacao anotacao
            join fetch anotacao.versao versao
            where versao.revisao.id = :revisaoId
            order by versao.numero desc, anotacao.pagina asc, anotacao.id asc
            """)
    List<RevisaoDocumentoAnotacao> findByRevisaoIdWithVersao(@Param("revisaoId") Long revisaoId);
}

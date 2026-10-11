package com.climb.api.repository;

import com.climb.api.model.SessaoAutenticacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface SessaoAutenticacaoRepository extends JpaRepository<SessaoAutenticacao, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessaoAutenticacao s join fetch s.usuario where s.id = :id")
    Optional<SessaoAutenticacao> buscarParaAtualizar(@Param("id") String id);

    @Query("""
            select count(s) > 0 from SessaoAutenticacao s join s.usuario u
            where s.id = :id and u.id = :usuarioId and u.situacao = 'ATIVO'
              and s.revogadaEm is null and s.expiraEm > :agora
              and s.versaoAutenticacao = u.versaoAutenticacao
            """)
    boolean estaAtiva(@Param("id") String id, @Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);
}

package com.climb.api.repository;

import com.climb.api.model.EmailNotificacaoPendente;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailNotificacaoPendenteRepository extends JpaRepository<EmailNotificacaoPendente, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select email from EmailNotificacaoPendente email
            where email.proximaTentativa <= :agora
            order by email.proximaTentativa, email.id
            """)
    List<EmailNotificacaoPendente> buscarParaEnviar(@Param("agora") LocalDateTime agora, Pageable limite);

    @Modifying
    @Query("delete from EmailNotificacaoPendente email where email.id = :id and email.tentativas = :tentativa")
    int confirmarEnvio(@Param("id") Long id, @Param("tentativa") int tentativa);

    @Modifying
    @Query("""
            update EmailNotificacaoPendente email set email.proximaTentativa = :proxima
            where email.id = :id and email.tentativas = :tentativa
            """)
    int reagendar(@Param("id") Long id, @Param("tentativa") int tentativa, @Param("proxima") LocalDateTime proxima);
}

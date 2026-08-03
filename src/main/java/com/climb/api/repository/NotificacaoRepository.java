package com.climb.api.repository;

import com.climb.api.model.Notificacao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByUsuario_Id(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario"})
    List<Notificacao> findByUsuario_IdOrderByDataCriacaoDescIdNotificacaoDesc(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario"})
    List<Notificacao> findByUsuario_IdAndLidaFalseOrderByDataCriacaoDescIdNotificacaoDesc(Long usuarioId);

    long countByUsuario_IdAndLidaFalse(Long usuarioId);

    Optional<Notificacao> findByIdNotificacaoAndUsuario_Id(Long idNotificacao, Long usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notificacao notificacao
            set notificacao.lida = true, notificacao.dataLeitura = :dataLeitura
            where notificacao.usuario.id = :usuarioId and notificacao.lida = false
            """)
    int marcarTodasComoLidas(
            @Param("usuarioId") Long usuarioId,
            @Param("dataLeitura") LocalDateTime dataLeitura);

    boolean existsByUsuario_IdAndMensagemAndTipoAndDataEnvio(Long usuarioId, String mensagem, String tipo, LocalDate dataEnvio);
}

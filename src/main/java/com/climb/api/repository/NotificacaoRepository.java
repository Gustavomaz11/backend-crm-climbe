package com.climb.api.repository;

import com.climb.api.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByUsuario_Id(Long usuarioId);

    List<Notificacao> findByUsuario_IdOrderByDataCriacaoDescIdNotificacaoDesc(Long usuarioId);

    List<Notificacao> findByUsuario_IdAndLidaFalseOrderByDataCriacaoDescIdNotificacaoDesc(Long usuarioId);

    long countByUsuario_IdAndLidaFalse(Long usuarioId);

    Optional<Notificacao> findByIdNotificacaoAndUsuario_Id(Long idNotificacao, Long usuarioId);

    boolean existsByUsuario_IdAndMensagemAndTipoAndDataEnvio(Long usuarioId, String mensagem, String tipo, LocalDate dataEnvio);
}

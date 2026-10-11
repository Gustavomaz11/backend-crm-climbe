package com.climb.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.climb.api.model.Reuniao;

public interface ReuniaoRepository extends JpaRepository<Reuniao, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Reuniao item set item.arquivadoEm = :agora " +
            "where item.arquivadoEm is null and item.arquivarApos <= :agora and item.status in ('REALIZADA', 'REALIZADO', 'CONCLUIDA', 'CONCLUIDO')")
    int arquivarVencidos(@org.springframework.data.repository.query.Param("agora") java.time.LocalDateTime agora);


    @Query("select r.idReuniao from Reuniao r where r.convitesNotificados = false and r.organizadorId is not null and r.googleEventId is not null and r.presencial = false and r.status <> 'CANCELADA'")
    List<Long> findIdsConvitesPendentes();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reuniao r where r.idReuniao = :id")
    java.util.Optional<Reuniao> findByIdForUpdate(@Param("id") Long id);

    List<Reuniao> findByEmpresa_IdEmpresa(Long empresaId);

    @Query("""
            select distinct r
            from Reuniao r
            join ParticipanteReuniao p on p.reuniao = r
            where p.usuario.id = :usuarioId
            """)
    List<Reuniao> findVisiveisParaUsuario(@Param("usuarioId") Long usuarioId);

    @Query("""
            select distinct r
            from Reuniao r
            join ParticipanteReuniao p on p.reuniao = r
            where p.usuario.id = :usuarioId
              and r.empresa.idEmpresa = :empresaId
            """)
    List<Reuniao> findByEmpresaVisiveisParaUsuario(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);

}

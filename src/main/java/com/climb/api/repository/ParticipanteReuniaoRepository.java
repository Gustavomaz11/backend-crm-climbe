package com.climb.api.repository;

import com.climb.api.model.ParticipanteReuniao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParticipanteReuniaoRepository extends JpaRepository<ParticipanteReuniao, Long> {

    @Query("select distinct p.usuario.id from ParticipanteReuniao p where p.reuniao.idReuniao in :ids")
    List<Long> buscarUsuarioIdsDasReunioes(@Param("ids") java.util.Collection<Long> ids);

    List<ParticipanteReuniao> findByReuniao_IdReuniao(Long reuniaoId);

    List<ParticipanteReuniao> findByReuniaoIn(List<com.climb.api.model.Reuniao> reunioes);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParticipanteReuniao p where p.reuniao.idReuniao = :reuniaoId and p.usuario.id = :usuarioId")
    java.util.Optional<ParticipanteReuniao> findConviteForUpdate(@Param("reuniaoId") Long reuniaoId, @Param("usuarioId") Long usuarioId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParticipanteReuniao p where p.id = :id")
    java.util.Optional<ParticipanteReuniao> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from ParticipanteReuniao p join fetch p.reuniao r
            where p.usuario.id = :usuarioId and p.resposta = com.climb.api.model.enums.RespostaConviteReuniao.PENDENTE and r.data >= :hoje
              and (r.status is null or r.status not in ('CANCELADA', 'CANCELADO'))
            order by r.data, r.hora
            """)
    List<ParticipanteReuniao> findConvitesPendentes(@Param("usuarioId") Long usuarioId, @Param("hoje") java.time.LocalDate hoje);

    @Query("""
            select p.id from ParticipanteReuniao p
            where p.resposta in (com.climb.api.model.enums.RespostaConviteReuniao.ACEITA, com.climb.api.model.enums.RespostaConviteReuniao.RECUSADA)
              and p.aceiteSincronizadoGoogle = false and p.reuniao.data >= :hoje
              and (p.reuniao.status is null or p.reuniao.status not in ('CANCELADA', 'CANCELADO'))
            """)
    List<Long> findIdsAceitesPendentesGoogle(@Param("hoje") java.time.LocalDate hoje);

    boolean existsByReuniao_IdReuniaoAndUsuario_Id(Long reuniaoId, Long usuarioId);

    @Query("""
            select pr
            from ParticipanteReuniao pr
            where pr.reuniao.idReuniao in (
                select pr2.reuniao.idReuniao
                from ParticipanteReuniao pr2
                where pr2.usuario.id = :usuarioId
            )
            """)
    List<ParticipanteReuniao> findVisiveisParaUsuario(@Param("usuarioId") Long usuarioId);

}

package com.climb.api.repository;

import com.climb.api.model.ParticipanteReuniao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParticipanteReuniaoRepository extends JpaRepository<ParticipanteReuniao, Long> {

    List<ParticipanteReuniao> findByReuniao_IdReuniao(Long reuniaoId);

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

package com.climb.api.repository;

import com.climb.api.model.ContratoApoioAtuacao;
import com.climb.api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ContratoApoioAtuacaoRepository extends JpaRepository<ContratoApoioAtuacao, Long> {
    List<ContratoApoioAtuacao> findByTarefa_IdTask(Long tarefaId);
    List<ContratoApoioAtuacao> findByTarefa_IdTaskAndFimIsNull(Long tarefaId);
    List<ContratoApoioAtuacao> findByContrato_IdContrato(Long contratoId);

    @Query("""
            select distinct atuacao.usuario from ContratoApoioAtuacao atuacao
            where atuacao.contrato.idContrato = :contratoId
              and atuacao.inicio < :proximoMes
              and (atuacao.fim is null or atuacao.fim >= :inicioMes)
            """)
    List<Usuario> buscarUsuariosNoMes(@Param("contratoId") Long contratoId,
            @Param("inicioMes") LocalDateTime inicioMes, @Param("proximoMes") LocalDateTime proximoMes);
}

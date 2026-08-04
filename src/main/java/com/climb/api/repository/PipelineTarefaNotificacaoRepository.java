package com.climb.api.repository;

import com.climb.api.model.PipelineTarefaNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PipelineTarefaNotificacaoRepository extends JpaRepository<PipelineTarefaNotificacao, Long> {

    @Query("select notificacao.chave from PipelineTarefaNotificacao notificacao where notificacao.chave in :chaves")
    List<String> findChavesExistentes(@Param("chaves") Collection<String> chaves);
}

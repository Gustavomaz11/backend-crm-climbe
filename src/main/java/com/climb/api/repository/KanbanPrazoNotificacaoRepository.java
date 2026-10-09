package com.climb.api.repository;

import com.climb.api.model.KanbanPrazoNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Set;

public interface KanbanPrazoNotificacaoRepository extends JpaRepository<KanbanPrazoNotificacao, Long> {
    @Query("select n.chave from KanbanPrazoNotificacao n where n.chave in :chaves")
    Set<String> buscarChaves(@Param("chaves") Collection<String> chaves);
}

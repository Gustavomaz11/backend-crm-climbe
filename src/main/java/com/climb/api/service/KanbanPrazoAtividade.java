package com.climb.api.service;

import com.climb.api.model.enums.TarefaTipo;
import java.time.LocalDate;

public record KanbanPrazoAtividade(TarefaTipo tipo, Long id, String titulo, String empresa,
        String servicos, String responsaveis, LocalDate prazo, int marco, String link) {
    public String chave(Long destinatarioId) {
        return tipo + ":" + id + ":" + prazo + ":" + marco + ":" + destinatarioId;
    }
}

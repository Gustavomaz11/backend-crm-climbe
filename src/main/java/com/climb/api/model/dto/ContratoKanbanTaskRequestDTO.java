package com.climb.api.model.dto;

import com.climb.api.model.enums.ContratoKanbanPrioridade;

import java.time.LocalDate;
import java.util.List;

public record ContratoKanbanTaskRequestDTO(
        Long raiaId,
        String titulo,
        String descricao,
        ContratoKanbanPrioridade prioridade,
        Long responsavelId,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer posicao,
        List<Long> responsavelIds,
        String justificativaAtraso
) {
    public ContratoKanbanTaskRequestDTO(Long raiaId, String titulo, String descricao, ContratoKanbanPrioridade prioridade,
            Long responsavelId, LocalDate dataInicio, LocalDate dataFim, Integer posicao, List<Long> responsavelIds) {
        this(raiaId, titulo, descricao, prioridade, responsavelId, dataInicio, dataFim, posicao, responsavelIds, null);
    }
    public ContratoKanbanTaskRequestDTO(Long raiaId, String titulo, String descricao, ContratoKanbanPrioridade prioridade,
            Long responsavelId, LocalDate dataInicio, LocalDate dataFim, Integer posicao) {
        this(raiaId, titulo, descricao, prioridade, responsavelId, dataInicio, dataFim, posicao, null, null);
    }
}

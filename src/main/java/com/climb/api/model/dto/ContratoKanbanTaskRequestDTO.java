package com.climb.api.model.dto;

import com.climb.api.model.enums.ContratoKanbanPrioridade;

import java.time.LocalDate;

public record ContratoKanbanTaskRequestDTO(
        Long raiaId,
        String titulo,
        String descricao,
        ContratoKanbanPrioridade prioridade,
        Long responsavelId,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer posicao
) {}

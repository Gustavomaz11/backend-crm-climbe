package com.climb.api.model.dto;

import java.time.LocalDate;

public record ContratoKanbanTaskRequestDTO(
        Long raiaId,
        String titulo,
        String descricao,
        Long responsavelId,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer posicao
) {}

package com.climb.api.model.dto;

public record ContratoKanbanSubtarefaRequestDTO(
        String titulo,
        Boolean concluida,
        Integer posicao
) {}

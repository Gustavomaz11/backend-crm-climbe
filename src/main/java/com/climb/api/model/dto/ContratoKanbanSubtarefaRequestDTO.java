package com.climb.api.model.dto;

public record ContratoKanbanSubtarefaRequestDTO(
        String titulo,
        Boolean concluida,
        Integer posicao,
        Long responsavelId
) {
    public ContratoKanbanSubtarefaRequestDTO(String titulo, Boolean concluida, Integer posicao) {
        this(titulo, concluida, posicao, null);
    }
}

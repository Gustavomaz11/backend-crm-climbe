package com.climb.api.model.dto;

public record ContratoKanbanRaiaRequestDTO(
        String titulo,
        Integer posicao,
        Boolean concluiTarefas
) {
    public ContratoKanbanRaiaRequestDTO(String titulo, Integer posicao) { this(titulo, posicao, null); }
}

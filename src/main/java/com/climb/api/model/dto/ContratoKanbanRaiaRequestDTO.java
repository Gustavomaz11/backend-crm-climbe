package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;

public record ContratoKanbanRaiaRequestDTO(
        String titulo,
        Integer posicao,
        Boolean concluiTarefas,
        ServicoComercial servico
) {
    public ContratoKanbanRaiaRequestDTO(String titulo, Integer posicao) { this(titulo, posicao, null, null); }
    public ContratoKanbanRaiaRequestDTO(String titulo, Integer posicao, Boolean concluiTarefas) { this(titulo, posicao, concluiTarefas, null); }
}

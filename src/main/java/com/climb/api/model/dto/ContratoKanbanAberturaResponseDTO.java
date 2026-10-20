package com.climb.api.model.dto;

public record ContratoKanbanAberturaResponseDTO(
        ContratoEquipeResponseDTO equipe, ContratoKanbanBoardResponseDTO board, boolean podeEncerrar) {
    public ContratoKanbanAberturaResponseDTO(ContratoEquipeResponseDTO equipe, ContratoKanbanBoardResponseDTO board) {
        this(equipe, board, board != null && board.gestor());
    }
}

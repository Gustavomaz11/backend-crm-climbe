package com.climb.api.model.dto;

public record ContratoKanbanMoverTaskRequestDTO(
        Long raiaId,
        String justificativaAtraso
) {
    public ContratoKanbanMoverTaskRequestDTO(Long raiaId) { this(raiaId, null); }
}

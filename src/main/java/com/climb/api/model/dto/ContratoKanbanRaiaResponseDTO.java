package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ContratoKanbanRaiaResponseDTO(
        Long id,
        String titulo,
        Integer posicao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        List<ContratoKanbanTaskResponseDTO> tasks
) {}

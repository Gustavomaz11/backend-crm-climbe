package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record ContratoKanbanSubtarefaResponseDTO(
        Long id,
        String titulo,
        boolean concluida,
        Integer posicao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}

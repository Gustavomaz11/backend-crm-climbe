package com.climb.api.model.dto;

public record PipelineSubtarefaResponseDTO(
        Long id,
        String titulo,
        boolean concluida,
        Integer posicao
) {}

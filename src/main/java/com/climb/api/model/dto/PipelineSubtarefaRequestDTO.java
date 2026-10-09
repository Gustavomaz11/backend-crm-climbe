package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineSubtarefaRequestDTO(
        @NotBlank @Size(max = 180) String titulo,
        boolean concluida,
        Integer posicao,
        Long responsavelId
) {
    public PipelineSubtarefaRequestDTO(String titulo, boolean concluida, Integer posicao) {
        this(titulo, concluida, posicao, null);
    }
}

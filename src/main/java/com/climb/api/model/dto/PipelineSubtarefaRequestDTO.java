package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineSubtarefaRequestDTO(
        @NotBlank @Size(max = 180) String titulo,
        boolean concluida,
        Integer posicao
) {}

package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineMotivoPerdaRequestDTO(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 500) String descricao,
        boolean ativo
) {}

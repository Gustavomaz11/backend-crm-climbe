package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineComentarioRequestDTO(
        @NotBlank @Size(max = 5000) String conteudo,
        Long comentarioPaiId
) {}

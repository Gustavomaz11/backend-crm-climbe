package com.climb.api.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PipelinePerdaRequestDTO(
        @NotNull Long motivoPerdaId,
        @Size(max = 500) String observacaoPerda
) {}

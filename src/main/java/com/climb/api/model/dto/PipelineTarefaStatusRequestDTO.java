package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineTarefaStatus;
import jakarta.validation.constraints.NotNull;

public record PipelineTarefaStatusRequestDTO(@NotNull PipelineTarefaStatus status,
        @jakarta.validation.constraints.Size(max = 4000) String justificativaAtraso) {
    public PipelineTarefaStatusRequestDTO(PipelineTarefaStatus status) { this(status, null); }
}

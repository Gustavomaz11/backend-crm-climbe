package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineTarefaStatus;
import jakarta.validation.constraints.NotNull;

public record PipelineTarefaStatusRequestDTO(@NotNull PipelineTarefaStatus status) {}

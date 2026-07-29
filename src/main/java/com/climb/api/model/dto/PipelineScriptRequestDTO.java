package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineScriptCanal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PipelineScriptRequestDTO(
        @NotBlank @Size(max = 160) String nome,
        @NotBlank @Size(max = 100) String categoria,
        @NotNull PipelineScriptCanal canal,
        @NotBlank String modeloMensagem,
        @NotNull Boolean ativo
) {}

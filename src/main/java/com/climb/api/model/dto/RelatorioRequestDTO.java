package com.climb.api.model.dto;

import jakarta.validation.constraints.NotNull;

public record RelatorioRequestDTO(
        @NotNull(message = "O ID do contrato é obrigatório")
        Long contratoId
) {}

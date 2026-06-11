package com.climb.api.model.dto;

import com.climb.api.model.enums.PropostaStatus;
import jakarta.validation.constraints.NotNull;

public record PropostaAprovacaoRequestDTO(
        @NotNull(message = "O status é obrigatório")
        PropostaStatus status
) {
}
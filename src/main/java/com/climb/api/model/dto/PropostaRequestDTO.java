package com.climb.api.model.dto;

import com.climb.api.model.enums.PropostaStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PropostaRequestDTO(
        @NotNull Long empresaId,
        @NotNull Long usuarioId,
        String url,
        @NotNull PropostaStatus status,
        LocalDate dataCriacao
) {
}
package com.climb.api.model.dto;

import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.validation.ValidPropostaStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PropostaUpdateDTO(
        @NotNull Long empresaId,
        @NotNull Long usuarioId,
        @NotNull @ValidPropostaStatus PropostaStatus status,
        LocalDate dataCriacao
) {
}
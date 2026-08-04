package com.climb.api.model.dto;

import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.validation.ValidPropostaStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PropostaRequestDTO(
        @NotNull Long empresaId,
        Long negocioId,
        @NotNull Long usuarioId,
        String url,
        @NotNull(message = "Informe o valuation da proposta")
        @DecimalMin(value = "0.01", message = "O valuation da proposta deve ser maior que zero")
        BigDecimal valuation,
        @NotNull @ValidPropostaStatus PropostaStatus status,
        LocalDate dataCriacao
) {
}

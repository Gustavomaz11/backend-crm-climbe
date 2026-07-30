package com.climb.api.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PropostaReajusteDTO(
        @NotNull @Min(1) @Max(24) Integer mesVigencia,
        @NotNull @DecimalMin("0.01") BigDecimal valor
) {}

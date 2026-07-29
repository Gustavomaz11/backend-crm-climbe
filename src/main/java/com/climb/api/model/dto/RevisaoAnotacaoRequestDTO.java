package com.climb.api.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RevisaoAnotacaoRequestDTO(
        @Min(1) int pagina,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal x,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal y,
        @NotNull @DecimalMin(value = "0.001") @DecimalMax("1.0") BigDecimal largura,
        @NotNull @DecimalMin(value = "0.001") @DecimalMax("1.0") BigDecimal altura,
        String cor,
        @NotBlank String comentario
) {
}

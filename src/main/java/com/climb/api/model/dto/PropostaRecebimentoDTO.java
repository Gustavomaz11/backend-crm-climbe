package com.climb.api.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record PropostaRecebimentoDTO(
        @NotNull @Min(1) @Max(24) Integer numero,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor,
        List<@NotNull @Valid PropostaRecebimentoServicoDTO> servicos
) {
    public PropostaRecebimentoDTO(Integer numero, BigDecimal valor) {
        this(numero, valor, null);
    }
}

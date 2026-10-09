package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PropostaRecebimentoServicoDTO(
        @NotNull ServicoComercial servico,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal valor
) {}

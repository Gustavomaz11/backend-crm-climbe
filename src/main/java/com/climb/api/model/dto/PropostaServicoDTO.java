package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PropostaServicoDTO(
        @NotNull(message = "Selecione o serviço da proposta.") ServicoComercial servico,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal comissaoTecnicoPercentual,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal comissaoComercialPercentual
) {}

package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PropostaComercialRequestDTO(
        @NotNull ServicoComercial servico,
        LocalDate mesInicio,
        @Min(0) @Max(24) Integer recorrenciaMeses,
        @Min(1) @Max(24) Integer quantidadeParcelas,
        Boolean parcelasIguais,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal comissaoTecnicoPercentual,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal comissaoComercialPercentual,
        List<Long> equipeTecnicaIds,
        List<Long> equipeComercialIds,
        List<@Valid PropostaReajusteDTO> reajustes,
        String observacoes
) {}

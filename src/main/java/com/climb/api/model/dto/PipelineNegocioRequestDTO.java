package com.climb.api.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PipelineNegocioRequestDTO(
        Long funilId,
        Long empresaId,
        @NotBlank String nomeEmpresa,
        @NotBlank String nomeContato,
        @NotBlank String telefone,
        @NotBlank @Email String email,
        @NotNull Long responsavelId,
        Long etapaId,
        LocalDateTime dataReuniao,
        @NotBlank String origemNegocio,
        @NotBlank String estrategiaComercial,
        @NotBlank String servicoInteresse,
        @DecimalMin(value = "0.01") BigDecimal valorEstimadoProposta,
        String observacoes
) {
}

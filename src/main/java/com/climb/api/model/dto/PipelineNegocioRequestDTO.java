package com.climb.api.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CNPJ;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        String servicoInteresse,
        @DecimalMin(value = "0.01") BigDecimal valorEstimadoProposta,
        String observacoes,
        @Size(max = 20) List<@NotBlank String> servicosInteresse,
        Boolean cadastrarEmpresa,
        @CNPJ String cnpj
) {
    public PipelineNegocioRequestDTO(
            Long funilId,
            Long empresaId,
            String nomeEmpresa,
            String nomeContato,
            String telefone,
            String email,
            Long responsavelId,
            Long etapaId,
            LocalDateTime dataReuniao,
            String origemNegocio,
            String estrategiaComercial,
            String servicoInteresse,
            BigDecimal valorEstimadoProposta,
            String observacoes
    ) {
        this(funilId, empresaId, nomeEmpresa, nomeContato, telefone, email, responsavelId,
                etapaId, dataReuniao, origemNegocio, estrategiaComercial, servicoInteresse,
                valorEstimadoProposta, observacoes, null, false, null);
    }
}

package com.climb.api.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PipelineEtapaConfiguracaoRequestDTO(
        Long id,
        @NotBlank @Size(max = 120) String nome,
        String objetivo,
        String criteriosConclusao,
        @Min(1) Integer tempoMaximoPermanenciaDias,
        boolean sucesso,
        boolean perda,
        List<String> camposObrigatorios,
        boolean ativo
) {}

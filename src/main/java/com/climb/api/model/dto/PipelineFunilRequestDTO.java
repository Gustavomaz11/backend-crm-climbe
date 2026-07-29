package com.climb.api.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PipelineFunilRequestDTO(
        @NotBlank @Size(max = 150) String nome,
        String descricao,
        @NotBlank @Size(max = 180) String estrategia,
        boolean ativo,
        @NotEmpty List<@Valid PipelineEtapaConfiguracaoRequestDTO> etapas
) {}

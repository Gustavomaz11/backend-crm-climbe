package com.climb.api.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record PipelineCampanhaRequestDTO(
        @NotBlank @Size(max = 160) String nome,
        @NotBlank @Size(max = 180) String estrategia,
        @Size(max = 500) String descricao,
        @NotNull Set<Long> leadIds,
        @NotEmpty Set<Long> participanteIds,
        @NotEmpty Set<Integer> diasExecucao,
        Set<Long> scriptIds,
        @NotEmpty List<@Valid PipelineCadenciaEtapaRequestDTO> etapas,
        @NotNull Boolean ativo
) {}

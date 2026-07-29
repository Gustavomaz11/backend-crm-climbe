package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineVendasResultado;

import java.time.LocalDate;

public record PipelineDashboardFiltroDTO(
        LocalDate dataInicio,
        LocalDate dataFim,
        Long responsavelId,
        Long funilId,
        String estrategia,
        String servico,
        String origem,
        Long empresaId,
        PipelineVendasResultado situacao
) {}

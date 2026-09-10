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
        PipelineVendasResultado situacao,
        Long campanhaId
) {
    public PipelineDashboardFiltroDTO(LocalDate dataInicio, LocalDate dataFim, Long responsavelId, Long funilId, String estrategia, String servico, String origem, Long empresaId, PipelineVendasResultado situacao) {
        this(dataInicio, dataFim, responsavelId, funilId, estrategia, servico, origem, empresaId, situacao, null);
    }
}

package com.climb.api.model.dto;

public record PipelineScriptDesempenhoDTO(
        Long scriptId,
        long tarefasGeradas,
        long tarefasConcluidas,
        double taxaConclusao
) {}

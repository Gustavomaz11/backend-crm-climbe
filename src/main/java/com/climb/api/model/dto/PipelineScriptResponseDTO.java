package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineScriptCanal;

import java.time.LocalDateTime;

public record PipelineScriptResponseDTO(
        Long id,
        String nome,
        String categoria,
        PipelineScriptCanal canal,
        String modeloMensagem,
        boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}

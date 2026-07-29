package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineHistoricoTipo;

import java.time.LocalDateTime;

public record PipelineHistoricoResponseDTO(
        Long id,
        PipelineHistoricoTipo tipo,
        String descricao,
        Long usuarioId,
        String usuarioNome,
        LocalDateTime criadoEm
) {}

package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record PipelineMotivoPerdaResponseDTO(
        Long id,
        String nome,
        String descricao,
        Integer posicao,
        boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}

package com.climb.api.model.dto;

public record PipelineFunilResumoResponseDTO(
        Long id,
        String nome,
        String descricao,
        String estrategia,
        Integer posicao,
        boolean ativo
) {}

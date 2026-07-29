package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineVendasResultado;

import java.util.List;

public record PipelineEtapaResponseDTO(
        Long id,
        String codigo,
        String nome,
        Integer posicao,
        PipelineVendasResultado resultado,
        String objetivo,
        String criteriosConclusao,
        Integer tempoMaximoPermanenciaDias,
        List<String> camposObrigatorios,
        List<PipelineNegocioResponseDTO> negocios
) {
}

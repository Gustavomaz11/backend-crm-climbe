package com.climb.api.model.dto;

import java.util.List;

public record PipelineEtapaConfiguracaoResponseDTO(
        Long id,
        String nome,
        String objetivo,
        String criteriosConclusao,
        Integer posicao,
        Integer tempoMaximoPermanenciaDias,
        boolean sucesso,
        boolean perda,
        List<String> camposObrigatorios,
        boolean ativo
) {}

package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PipelineFunilResponseDTO(
        Long id,
        String nome,
        String descricao,
        String estrategia,
        Integer posicao,
        boolean ativo,
        List<PipelineEtapaConfiguracaoResponseDTO> etapas,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        String tipo
) {
    public PipelineFunilResponseDTO(Long id, String nome, String descricao, String estrategia, Integer posicao, boolean ativo, List<PipelineEtapaConfiguracaoResponseDTO> etapas, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this(id, nome, descricao, estrategia, posicao, ativo, etapas, criadoEm, atualizadoEm, "VENDAS");
    }
}

package com.climb.api.model.dto;

public record PipelineFunilResumoResponseDTO(
        Long id,
        String nome,
        String descricao,
        String estrategia,
        Integer posicao,
        boolean ativo,
        String tipo
) {
    public PipelineFunilResumoResponseDTO(Long id, String nome, String descricao, String estrategia, Integer posicao, boolean ativo) {
        this(id, nome, descricao, estrategia, posicao, ativo, "VENDAS");
    }
}

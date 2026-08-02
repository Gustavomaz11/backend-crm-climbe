package com.climb.api.model.dto;

import com.climb.api.model.Permissao;

import java.util.Set;

public record GrupoPermissaoResponseDTO(
        Long id,
        String nome,
        String descricao,
        Set<Permissao> permissoes
) {
}

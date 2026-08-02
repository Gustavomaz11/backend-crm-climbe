package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record GrupoPermissaoRequestDTO(
        @NotBlank String nome,
        String descricao,
        Set<Long> permissaoIds
) {
}

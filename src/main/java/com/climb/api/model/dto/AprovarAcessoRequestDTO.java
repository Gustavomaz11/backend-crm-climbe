package com.climb.api.model.dto;

import java.util.Set;

public record AprovarAcessoRequestDTO(
        Long cargoId,
        Set<Long> permissaoIds
) {
}

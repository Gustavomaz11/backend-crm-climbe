package com.climb.api.model.dto;

public record CargoHierarquiaItemRequestDTO(
        Long cargoId,
        Long cargoSuperiorId,
        Integer ordem
) {
}

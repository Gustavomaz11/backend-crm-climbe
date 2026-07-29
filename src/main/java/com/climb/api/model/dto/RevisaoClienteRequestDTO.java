package com.climb.api.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RevisaoClienteRequestDTO(
        @NotEmpty List<@Valid RevisaoAnotacaoRequestDTO> anotacoes,
        String comentarioGeral
) {
}

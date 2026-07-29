package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;

public record RevisaoReprovacaoRequestDTO(@NotBlank String justificativa) {
}

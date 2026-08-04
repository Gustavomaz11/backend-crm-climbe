package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrupoCargoRequestDTO(
        @NotBlank(message = "Nome do grupo e obrigatorio")
        @Size(max = 120, message = "Nome do grupo deve ter no maximo 120 caracteres")
        String nome,
        @Size(max = 500, message = "Descricao deve ter no maximo 500 caracteres")
        String descricao
) {}

package com.climb.api.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ContratoEquipeRequestDTO(
        @NotEmpty(message = "Selecione pelo menos uma pessoa para a equipe do contrato.")
        List<@NotNull @Positive Long> usuarioIds
) {}

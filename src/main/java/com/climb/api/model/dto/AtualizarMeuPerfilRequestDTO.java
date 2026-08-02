package com.climb.api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AtualizarMeuPerfilRequestDTO(
        @NotBlank String nomeCompleto,
        @NotBlank @Email String email,
        @NotBlank String cpf,
        @NotBlank String contato
) {
}

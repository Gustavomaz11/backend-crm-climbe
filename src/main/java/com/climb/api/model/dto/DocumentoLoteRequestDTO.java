package com.climb.api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DocumentoLoteRequestDTO(
        @NotNull Long empresaId,
        @NotEmpty List<String> documentos,
        @Email String emailDestinatario
) {}

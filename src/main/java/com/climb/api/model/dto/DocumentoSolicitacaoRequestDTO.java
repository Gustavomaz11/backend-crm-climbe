package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentoSolicitacaoRequestDTO(
        @NotNull(message = "O ID da empresa é obrigatório")
        Long empresaId,

        @NotBlank(message = "O título do documento é obrigatório")
        String titulo,

        String tipoDocumento,

        @NotBlank(message = "O e-mail do destinatário é obrigatório")
        String emailDestinatario
) {}

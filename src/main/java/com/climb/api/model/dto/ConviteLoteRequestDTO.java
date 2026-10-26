package com.climb.api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;

public record ConviteLoteRequestDTO(
        @NotEmpty(message = "Informe pelo menos um e-mail.")
        @Size(max = 100, message = "Envie no máximo 100 e-mails por lote.")
        List<@NotBlank @Email @Size(max = 254) String> emails) {
    public ConviteLoteRequestDTO {
        if (emails != null) {
            emails = emails.stream().map(email -> email == null ? null : email.trim().toLowerCase(Locale.ROOT)).toList();
        }
    }
}

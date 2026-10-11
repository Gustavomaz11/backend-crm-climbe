package com.climb.api.model.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class RefreshTokenRequestDTO {
    @NotBlank(message = "Informe o token da sessão.")
    private String refreshToken;
}

package com.climb.api.model.dto;

public record RefreshTokenResponseDTO(
        String accessToken,
        long expiresIn
) {
}

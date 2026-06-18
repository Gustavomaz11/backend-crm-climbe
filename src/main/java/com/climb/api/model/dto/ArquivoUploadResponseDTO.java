package com.climb.api.model.dto;

public record ArquivoUploadResponseDTO(
        String nomeOriginal,
        String contentType,
        long tamanho,
        String chave,
        String url
) {
}

package com.climb.api.service;

public record ArquivoValidado(
        String nomeOriginal,
        String contentType,
        long tamanho,
        byte[] conteudo
) {
}

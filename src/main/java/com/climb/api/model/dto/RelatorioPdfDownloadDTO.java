package com.climb.api.model.dto;

public record RelatorioPdfDownloadDTO(
        String nomeArquivo,
        byte[] conteudo
) {}

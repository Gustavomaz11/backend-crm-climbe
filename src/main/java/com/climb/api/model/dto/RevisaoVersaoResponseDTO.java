package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RevisaoVersaoResponseDTO(
        Long id,
        int numero,
        String nomeArquivo,
        String contentType,
        int totalPaginas,
        String resultado,
        String comentarioGeral,
        String justificativa,
        LocalDateTime criadoEm,
        LocalDateTime respondidoEm,
        List<RevisaoAnotacaoResponseDTO> anotacoes
) {
}

package com.climb.api.model.dto;

import java.math.BigDecimal;

public record RevisaoAnotacaoResponseDTO(
        Long id,
        int pagina,
        BigDecimal x,
        BigDecimal y,
        BigDecimal largura,
        BigDecimal altura,
        String cor,
        String comentario
) {
}

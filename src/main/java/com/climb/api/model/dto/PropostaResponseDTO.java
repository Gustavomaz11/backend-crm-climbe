package com.climb.api.model.dto;

import com.climb.api.model.enums.PropostaStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PropostaResponseDTO(
        Long idProposta,
        Long empresaId,
        Long usuarioId,
        String url,
        BigDecimal valuation,
        PropostaStatus status,
        LocalDate dataCriacao
) {
}

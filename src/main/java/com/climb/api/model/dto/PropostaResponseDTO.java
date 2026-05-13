package com.climb.api.model.dto;

import com.climb.api.model.enums.PropostaStatus;
import java.time.LocalDate;

public record PropostaResponseDTO(
        Long idProposta,
        Long empresaId,
        Long usuarioId,
        String url,
        PropostaStatus status,
        LocalDate dataCriacao
) {
}
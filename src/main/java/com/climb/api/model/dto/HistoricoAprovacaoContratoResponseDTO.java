package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record HistoricoAprovacaoContratoResponseDTO(
        Long idHistorico,
        Long contratoId,
        Long usuarioId,
        String usuarioNome,
        String statusAnterior,
        String statusNovo,
        LocalDateTime dataAlteracao
) {
}

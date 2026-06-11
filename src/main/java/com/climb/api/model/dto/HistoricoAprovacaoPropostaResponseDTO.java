package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record HistoricoAprovacaoPropostaResponseDTO(
        Long idHistorico,
        Long propostaId,
        Long usuarioId,
        String statusAnterior,
        String statusNovo,
        LocalDateTime dataAlteracao
) {
}

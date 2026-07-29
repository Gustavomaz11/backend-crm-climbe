package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineVendasResultado;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PipelineNegocioResponseDTO(
        Long id,
        Long funilId,
        String funilNome,
        Long empresaId,
        String nomeEmpresa,
        String nomeContato,
        String telefone,
        String email,
        Long responsavelId,
        String responsavelNome,
        Long etapaId,
        String etapaCodigo,
        String etapaNome,
        LocalDateTime dataReuniao,
        String origemNegocio,
        String estrategiaComercial,
        String servicoInteresse,
        BigDecimal valorEstimadoProposta,
        String observacoes,
        PipelineVendasResultado resultado,
        Long motivoPerdaId,
        String motivoPerdaNome,
        String observacaoPerda,
        LocalDateTime encerradoEm,
        Long contratoId,
        LocalDateTime criadoEm,
        LocalDateTime ultimaMovimentacaoEm
) {
}

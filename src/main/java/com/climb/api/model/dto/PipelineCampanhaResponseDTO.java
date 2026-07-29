package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record PipelineCampanhaResponseDTO(
        Long id,
        String nome,
        String estrategia,
        String descricao,
        boolean ativo,
        Set<Long> leadIds,
        Set<Long> participanteIds,
        Set<Long> scriptIds,
        Set<Integer> diasExecucao,
        List<PipelineCadenciaEtapaResponseDTO> etapas,
        long execucoesAtivas,
        long execucoesConcluidas,
        long semResposta,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}

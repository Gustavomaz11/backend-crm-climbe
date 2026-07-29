package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineTarefaPrioridade;
import com.climb.api.model.enums.PipelineTarefaStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PipelineTarefaResponseDTO(
        Long id,
        Long negocioId,
        String negocioNome,
        String titulo,
        String descricao,
        Long responsavelId,
        String responsavelNome,
        LocalDate dataInicio,
        LocalDate prazo,
        PipelineTarefaPrioridade prioridade,
        PipelineTarefaStatus status,
        String tipo,
        String observacoes,
        List<PipelineSubtarefaResponseDTO> subtarefas,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        LocalDateTime concluidoEm
) {}

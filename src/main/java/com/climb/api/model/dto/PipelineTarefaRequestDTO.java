package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineTarefaPrioridade;
import com.climb.api.model.enums.PipelineTarefaStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PipelineTarefaRequestDTO(
        @NotBlank @Size(max = 180) String titulo,
        String descricao,
        @NotNull Long responsavelId,
        LocalDate dataInicio,
        LocalDate prazo,
        @NotNull PipelineTarefaPrioridade prioridade,
        @NotNull PipelineTarefaStatus status,
        @NotBlank @Size(max = 100) String tipo,
        String observacoes,
        List<@Valid PipelineSubtarefaRequestDTO> subtarefas
) {}

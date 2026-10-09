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
        Long responsavelId,
        LocalDate dataInicio,
        @NotNull LocalDate prazo,
        @NotNull PipelineTarefaPrioridade prioridade,
        @NotNull PipelineTarefaStatus status,
        @NotBlank @Size(max = 100) String tipo,
        String observacoes,
        List<@Valid PipelineSubtarefaRequestDTO> subtarefas,
        List<Long> responsavelIds,
        @Size(max = 4000) String justificativaAtraso
) {
    public PipelineTarefaRequestDTO(String titulo, String descricao, Long responsavelId, LocalDate dataInicio,
            LocalDate prazo, PipelineTarefaPrioridade prioridade, PipelineTarefaStatus status, String tipo,
            String observacoes, List<PipelineSubtarefaRequestDTO> subtarefas, List<Long> responsavelIds) {
        this(titulo, descricao, responsavelId, dataInicio, prazo, prioridade, status, tipo, observacoes, subtarefas, responsavelIds, null);
    }
    public PipelineTarefaRequestDTO(String titulo, String descricao, Long responsavelId, LocalDate dataInicio,
            LocalDate prazo, PipelineTarefaPrioridade prioridade, PipelineTarefaStatus status, String tipo,
            String observacoes, List<PipelineSubtarefaRequestDTO> subtarefas) {
        this(titulo, descricao, responsavelId, dataInicio, prazo, prioridade, status, tipo, observacoes, subtarefas, null, null);
    }
}

package com.climb.api.model.dto;

import com.climb.api.model.enums.ContratoKanbanPrioridade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ContratoKanbanTaskResponseDTO(
        Long id,
        Long raiaId,
        String titulo,
        String descricao,
        ContratoKanbanPrioridade prioridade,
        UsuarioResumoDTO responsavel,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer posicao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        List<ContratoKanbanSubtarefaResponseDTO> subtarefas
) {}

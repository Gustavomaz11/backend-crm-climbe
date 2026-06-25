package com.climb.api.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContratoKanbanTaskResponseDTO(
        Long id,
        Long raiaId,
        String titulo,
        String descricao,
        UsuarioResumoDTO responsavel,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer posicao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}

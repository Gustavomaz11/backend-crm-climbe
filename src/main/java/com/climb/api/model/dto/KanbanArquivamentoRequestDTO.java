package com.climb.api.model.dto;

import jakarta.validation.constraints.NotNull;

public record KanbanArquivamentoRequestDTO(@NotNull Boolean arquivado) {}

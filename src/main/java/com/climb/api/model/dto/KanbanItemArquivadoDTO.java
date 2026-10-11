package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record KanbanItemArquivadoDTO(Long id, String titulo, String coluna,
                                    String responsaveis, LocalDateTime arquivadoEm, Object item) {}

package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record PipelineComentarioResponseDTO(
        Long id,
        Long autorId,
        String autorNome,
        String conteudo,
        LocalDateTime criadoEm
) {}

package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TarefaComentarioResponseDTO(Long id, Long comentarioPaiId, UsuarioResumoDTO autor,
        String conteudo, LocalDateTime criadoEm, List<TarefaAnexoResponseDTO> anexos) {}

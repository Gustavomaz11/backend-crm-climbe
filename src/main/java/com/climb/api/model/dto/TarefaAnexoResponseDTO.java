package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record TarefaAnexoResponseDTO(Long id, String nome, String contentType, long tamanho,
        UsuarioResumoDTO autor, LocalDateTime criadoEm) {}

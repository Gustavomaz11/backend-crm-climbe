package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record TarefaPastaResponseDTO(Long id, String nome, Long pastaPaiId, UsuarioResumoDTO autor, LocalDateTime criadoEm) {}

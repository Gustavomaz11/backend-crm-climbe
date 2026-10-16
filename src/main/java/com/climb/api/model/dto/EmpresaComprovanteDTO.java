package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record EmpresaComprovanteDTO(Long id, String tipo, String status, String mensagem, LocalDateTime geradoEm) {}

package com.climb.api.model.dto;

import java.time.LocalDateTime;

public record DocumentoUploadInfoResponseDTO(
        Long id,
        String titulo,
        String tipoDocumento,
        Long empresaId,
        String nomeEmpresa,
        LocalDateTime tokenExpiraEm
) {}

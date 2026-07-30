package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentoLoteResponseDTO(
        Long id,
        Long empresaId,
        String nomeEmpresa,
        String emailDestinatario,
        LocalDateTime tokenExpiraEm,
        List<DocumentoResponseDTO> documentos
) {}

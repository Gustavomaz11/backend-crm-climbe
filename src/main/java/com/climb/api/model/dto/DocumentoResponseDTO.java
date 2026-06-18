package com.climb.api.model.dto;

import com.climb.api.model.enums.DocumentoStatus;
import java.time.LocalDateTime;

public record DocumentoResponseDTO(
        Long id,
        Long empresaId,
        String nomeEmpresa,
        String titulo,
        String tipoDocumento,
        String url,
        DocumentoStatus validado,
        Long analistaId,
        String nomeAnalista,
        String emailDestinatario,
        LocalDateTime tokenExpiraEm,
        LocalDateTime dataSolicitacao,
        LocalDateTime dataEnvio
) {}

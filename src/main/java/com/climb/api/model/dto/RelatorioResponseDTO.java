package com.climb.api.model.dto;

import java.time.LocalDate;

public record RelatorioResponseDTO(
        Long idRelatorio,
        Long contratoId,
        String urlPdf,
        LocalDate dataEnvio
) {}

package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RevisaoDocumentoResponseDTO(
        Long id,
        String tipo,
        Long referenciaId,
        String empresaNome,
        String destinatarioEmail,
        String destinatarioNome,
        String status,
        int versaoAtual,
        String nomeArquivo,
        String contentType,
        int totalPaginas,
        String justificativa,
        LocalDateTime tokenExpiraEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        LocalDateTime respondidoEm,
        String emailStatus,
        LocalDateTime emailEnviadoEm,
        List<RevisaoVersaoResponseDTO> versoes
) {
}

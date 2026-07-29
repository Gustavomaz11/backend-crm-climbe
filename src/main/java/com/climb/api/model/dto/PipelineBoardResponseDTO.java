package com.climb.api.model.dto;

import java.util.List;

public record PipelineBoardResponseDTO(
        Long funilId,
        String funilNome,
        List<PipelineEtapaResponseDTO> etapas
) {
}

package com.climb.api.model.dto;

import jakarta.validation.constraints.NotNull;

public record PipelineMoverNegocioRequestDTO(
        @NotNull Long etapaId,
        Long motivoPerdaId,
        @jakarta.validation.constraints.Size(max = 500) String observacaoPerda,
        Long responsavelTecnicoId,
        Long propostaId
) {
    public PipelineMoverNegocioRequestDTO(Long etapaId, Long motivoPerdaId, String observacaoPerda) {
        this(etapaId, motivoPerdaId, observacaoPerda, null, null);
    }
}

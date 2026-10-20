package com.climb.api.model.dto;

import jakarta.validation.constraints.NotNull;

public record PipelineMoverNegocioRequestDTO(
        @NotNull Long etapaId,
        Long motivoPerdaId,
        @jakarta.validation.constraints.Size(max = 500) String observacaoPerda,
        Long responsavelTecnicoId,
        Long propostaId,
        java.util.List<PipelineResponsavelServicoRequestDTO> responsaveisPorServico
) {
    public PipelineMoverNegocioRequestDTO(Long etapaId, Long motivoPerdaId, String observacaoPerda,
            Long responsavelTecnicoId, Long propostaId) {
        this(etapaId, motivoPerdaId, observacaoPerda, responsavelTecnicoId, propostaId, null);
    }
    public PipelineMoverNegocioRequestDTO(Long etapaId, Long motivoPerdaId, String observacaoPerda) {
        this(etapaId, motivoPerdaId, observacaoPerda, null, null);
    }
}

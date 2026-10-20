package com.climb.api.model.dto;

public record PipelineGanhoRequestDTO(Long responsavelTecnicoId, Long propostaId,
        java.util.List<PipelineResponsavelServicoRequestDTO> responsaveisPorServico) {
    public PipelineGanhoRequestDTO(Long responsavelTecnicoId, Long propostaId) {
        this(responsavelTecnicoId, propostaId, null);
    }
}

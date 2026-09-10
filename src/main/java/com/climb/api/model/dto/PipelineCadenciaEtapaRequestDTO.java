package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineCadenciaTipo;
import com.climb.api.model.enums.PipelineTarefaPrioridade;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PipelineCadenciaEtapaRequestDTO(
        @NotNull PipelineCadenciaTipo tipo,
        @Size(max = 180) String titulo,
        String descricao,
        @Size(max = 100) String tipoTarefa,
        PipelineTarefaPrioridade prioridade,
        @Min(0) Integer diasUteisEspera,
        @Min(0) Integer prazoDiasUteis,
        Long scriptId,
        String etapaFunilCodigo
) {
    public PipelineCadenciaEtapaRequestDTO(PipelineCadenciaTipo tipo, String titulo, String descricao, String tipoTarefa, PipelineTarefaPrioridade prioridade, Integer diasUteisEspera, Integer prazoDiasUteis, Long scriptId) {
        this(tipo, titulo, descricao, tipoTarefa, prioridade, diasUteisEspera, prazoDiasUteis, scriptId, null);
    }
}

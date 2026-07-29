package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineCadenciaTipo;
import com.climb.api.model.enums.PipelineTarefaPrioridade;

public record PipelineCadenciaEtapaResponseDTO(
        Long id,
        int ordem,
        PipelineCadenciaTipo tipo,
        String titulo,
        String descricao,
        String tipoTarefa,
        PipelineTarefaPrioridade prioridade,
        int diasUteisEspera,
        Integer prazoDiasUteis,
        Long scriptId,
        String scriptNome
) {}

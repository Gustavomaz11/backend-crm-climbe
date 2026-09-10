package com.climb.api.model.dto;

public record PipelineCampanhaLeadDTO(
        Long id,
        String empresa,
        String contato,
        String responsavel,
        String servico,
        String funil,
        String etapa,
        String tipoFunil,
        Long campanhaOrigemId
) {}

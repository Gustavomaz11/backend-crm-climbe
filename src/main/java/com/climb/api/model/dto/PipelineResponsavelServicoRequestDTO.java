package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;

public record PipelineResponsavelServicoRequestDTO(ServicoComercial servico, Long responsavelTecnicoId) {}

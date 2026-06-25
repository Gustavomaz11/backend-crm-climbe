package com.climb.api.model.dto;

import java.util.List;

public record ContratoResponsaveisRequestDTO(
        Long responsavelId,
        List<Long> participanteIds
) {}

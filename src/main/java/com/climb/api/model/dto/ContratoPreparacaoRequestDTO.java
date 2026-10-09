package com.climb.api.model.dto;

import com.climb.api.model.enums.ContratoPreparacaoEtapa;
import jakarta.validation.constraints.NotNull;

public record ContratoPreparacaoRequestDTO(@NotNull ContratoPreparacaoEtapa etapa) {}

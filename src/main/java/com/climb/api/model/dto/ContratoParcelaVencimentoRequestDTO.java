package com.climb.api.model.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ContratoParcelaVencimentoRequestDTO(@NotNull LocalDate vencimento) {}

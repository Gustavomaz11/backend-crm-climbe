package com.climb.api.model.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PipelineOrdemRequestDTO(@NotEmpty List<Long> ids) {}

package com.climb.api.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ReuniaoDisponibilidadeRequestDTO(
        @NotNull LocalDate data, @NotNull LocalTime hora, @NotNull LocalTime horaFim,
        Long reuniaoId, @Size(max = 500) List<String> salaEmails,
        @Size(max = 500) List<Long> participanteIds, String local) { }

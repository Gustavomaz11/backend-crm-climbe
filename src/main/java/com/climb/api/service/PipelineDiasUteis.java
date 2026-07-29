package com.climb.api.service;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

@Component
class PipelineDiasUteis {
    LocalDate adicionar(LocalDate inicio, int dias) {
        LocalDate data = inicio;
        int restantes = dias;
        while (restantes > 0) {
            data = data.plusDays(1);
            if (ehDiaUtil(data)) restantes--;
        }
        return data;
    }

    LocalDateTime aposEspera(LocalDateTime inicio, int diasUteis, Set<Integer> diasExecucao) {
        LocalDate data = adicionar(inicio.toLocalDate(), diasUteis);
        return proximoPermitido(LocalDateTime.of(data, LocalTime.of(9, 0)), diasExecucao);
    }

    LocalDateTime proximoPermitido(LocalDateTime data, Set<Integer> diasExecucao) {
        if (diasExecucao.contains(data.getDayOfWeek().getValue())) return data;
        LocalDate proxima = data.toLocalDate();
        do { proxima = proxima.plusDays(1); }
        while (!diasExecucao.contains(proxima.getDayOfWeek().getValue()));
        return LocalDateTime.of(proxima, LocalTime.of(9, 0));
    }

    private boolean ehDiaUtil(LocalDate data) {
        return data.getDayOfWeek() != DayOfWeek.SATURDAY && data.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}

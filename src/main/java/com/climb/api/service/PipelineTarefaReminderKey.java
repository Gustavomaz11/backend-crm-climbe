package com.climb.api.service;

import com.climb.api.model.enums.PipelineTarefaNotificacaoTipo;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

final class PipelineTarefaReminderKey {

    private PipelineTarefaReminderKey() {}

    static String weeklyReference(LocalDate date) {
        WeekFields iso = WeekFields.ISO;
        return date.get(iso.weekBasedYear()) + "-W"
                + String.format("%02d", date.get(iso.weekOfWeekBasedYear()));
    }

    static String weekly(Long userId, String reference) {
        return PipelineTarefaNotificacaoTipo.RESUMO_SEMANAL.name() + ":" + userId + ":" + reference;
    }

    static String task(PipelineTarefaNotificacaoTipo type,
                       Long taskId,
                       Long recipientId,
                       String reference) {
        return type.name() + ":" + taskId + ":" + recipientId + ":" + reference;
    }
}

package com.climb.api.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TarefaPrazoPolicy {
    private TarefaPrazoPolicy() {}

    public static LocalDate hoje() { return LocalDate.now(ZoneId.of("America/Sao_Paulo")); }

    public static String justificarConclusao(LocalDate prazo, String justificativa, LocalDate hoje) {
        if (prazo == null || !prazo.isBefore(hoje)) return null;
        if (justificativa == null || justificativa.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta tarefa está atrasada. Conte o motivo do atraso para poder concluí-la.");
        }
        String texto = justificativa.trim();
        if (texto.length() > 4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A justificativa pode ter até 4.000 caracteres.");
        return texto;
    }
}

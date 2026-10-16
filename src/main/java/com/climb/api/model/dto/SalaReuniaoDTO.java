package com.climb.api.model.dto;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import java.util.List;
import java.util.Locale;

public record SalaReuniaoDTO(String email, String nome, Integer capacidade) {
    public static List<SalaReuniaoDTO> fromGoogleEvent(Event event) {
        if (event == null || event.getAttendees() == null) return List.of();
        var recursos = event.getAttendees().stream()
                .filter(convidado -> convidado.getEmail() != null && !convidado.getEmail().isBlank())
                .filter(SalaReuniaoDTO::isResource)
                .toList();
        return recursos.stream().map(recurso -> {
            String email = recurso.getEmail().trim().toLowerCase(Locale.ROOT);
            String nome = recurso.getDisplayName();
            if (nome == null || nome.isBlank()) {
                nome = recursos.size() == 1 && event.getLocation() != null && !event.getLocation().isBlank()
                        ? event.getLocation() : email;
            }
            return new SalaReuniaoDTO(email, nome, null);
        }).toList();
    }

    public static boolean isResource(EventAttendee convidado) {
        return Boolean.TRUE.equals(convidado.getResource()) || (convidado.getEmail() != null
                && convidado.getEmail().toLowerCase(Locale.ROOT).endsWith("@resource.calendar.google.com"));
    }
}

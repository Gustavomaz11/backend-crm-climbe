package com.climb.api.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;

@Service
public class GoogleDisponibilidadeService {
    private static final Logger log = LoggerFactory.getLogger(GoogleDisponibilidadeService.class);
    private final GoogleCalendarService calendarService;

    public GoogleDisponibilidadeService(GoogleCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    // null indica uma agenda que o Google não permitiu consultar.
    public Map<String, Boolean> consultar(String token, List<String> emails, Instant inicio, Instant fim,
                                          String eventoIgnorado) {
        Map<String, Boolean> resultado = new LinkedHashMap<>();
        emails.forEach(email -> resultado.put(email, null));
        if (emails.isEmpty()) return resultado;
        try {
            Calendar calendar = calendarService.buildCalendar(token);
            for (int offset = 0; offset < emails.size(); offset += 50) {
                var lote = emails.subList(offset, Math.min(offset + 50, emails.size()));
                var request = new FreeBusyRequest().setTimeMin(new DateTime(inicio.toEpochMilli()))
                        .setTimeMax(new DateTime(fim.toEpochMilli())).setTimeZone("America/Sao_Paulo")
                        .setItems(lote.stream().map(email -> new FreeBusyRequestItem().setId(email)).toList());
                var response = calendar.freebusy().query(request).execute();
                if (response.getCalendars() == null) continue;
                for (String email : lote) {
                    var agenda = response.getCalendars().get(email);
                    if (agenda == null || agenda.getErrors() != null && !agenda.getErrors().isEmpty()) continue;
                    boolean ocupado = agenda.getBusy() != null && agenda.getBusy().stream().anyMatch(periodo ->
                            periodo.getStart().getValue() < fim.toEpochMilli()
                                    && periodo.getEnd().getValue() > inicio.toEpochMilli());
                    resultado.put(email, !ocupado);
                }
            }
            if (eventoIgnorado != null && resultado.containsValue(false)) {
                Event original = calendar.events().get("primary", eventoIgnorado).execute();
                for (String email : emails) {
                    if (Boolean.FALSE.equals(resultado.get(email))) {
                        resultado.put(email, livreSemProprioEvento(calendar, email, inicio, fim, original));
                    }
                }
            }
        } catch (Exception e) {
            // Falhas de consulta nunca devem transformar uma agenda desconhecida em disponível.
            if (eventoIgnorado != null) resultado.replaceAll((email, livre) -> Boolean.FALSE.equals(livre) ? null : livre);
            log.warn("Não foi possível concluir a consulta de disponibilidade no Google Agenda: {}", e.getClass().getSimpleName());
        }
        return resultado;
    }

    private Boolean livreSemProprioEvento(Calendar calendar, String email, Instant inicio, Instant fim, Event original) {
        try {
            String pageToken = null;
            do {
                var page = calendar.events().list(email).setTimeMin(new DateTime(inicio.toEpochMilli()))
                        .setTimeMax(new DateTime(fim.toEpochMilli())).setSingleEvents(true)
                        .setShowDeleted(false).setPageToken(pageToken).execute();
                if (page.getItems() != null && page.getItems().stream()
                        .anyMatch(event -> bloqueia(event, original, email))) return false;
                pageToken = page.getNextPageToken();
            } while (pageToken != null);
            return true;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean bloqueia(Event event, Event original, String email) {
        if ("cancelled".equals(event.getStatus()) || "transparent".equals(event.getTransparency())) return false;
        if (original.getId() != null && original.getId().equals(event.getId())) return false;
        if (original.getICalUID() != null && original.getICalUID().equals(event.getICalUID())) return false;
        return event.getAttendees() == null || event.getAttendees().stream().noneMatch(attendee ->
                email.equalsIgnoreCase(attendee.getEmail()) && "declined".equals(attendee.getResponseStatus()));
    }
}

package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleParticipanteDisponibilidadeService {
    private static final Logger log = LoggerFactory.getLogger(GoogleParticipanteDisponibilidadeService.class);
    private final GoogleCalendarService calendar;
    private final GoogleCredentialService credentials;

    public GoogleParticipanteDisponibilidadeService(GoogleCalendarService calendar, GoogleCredentialService credentials) {
        this.calendar = calendar;
        this.credentials = credentials;
    }

    public Map<Long, Boolean> consultar(String token, List<Usuario> usuarios, Instant inicio, Instant fim, String eventoIgnorado) {
        Map<Long, Boolean> resultado = new LinkedHashMap<>();
        usuarios.forEach(usuario -> resultado.put(usuario.getId(), null));
        if (usuarios.isEmpty()) return resultado;
        Event original = null;
        if (StringUtils.hasText(eventoIgnorado)) {
            try {
                original = calendar.buscarEvento(eventoIgnorado, token);
                if (original == null) return resultado;
            } catch (Exception e) {
                log.warn("Não foi possível identificar a reunião em edição no Google Agenda.");
                return resultado;
            }
        }
        for (Usuario usuario : usuarios) {
            resultado.put(usuario.getId(), consultarUsuario(token, usuario, inicio, fim, original));
        }
        return resultado;
    }

    private Boolean consultarUsuario(String token, Usuario usuario, Instant inicio, Instant fim, Event original) {
        try {
            String proprioToken = credentials.obterAccessToken(usuario.getId()).filter(StringUtils::hasText).orElse(null);
            String agendaId = proprioToken == null ? usuario.getEmail() : "primary";
            if (!StringUtils.hasText(agendaId)) return null;
            var client = calendar.buildCalendar(proprioToken == null ? token : proprioToken);
            String pageToken = null;
            boolean respostaOmitida = false;
            do {
                var page = client.events().list(agendaId).setTimeMin(new DateTime(inicio.toEpochMilli()))
                        .setTimeMax(new DateTime(fim.toEpochMilli())).setSingleEvents(true).setShowDeleted(false)
                        .setFields("accessRole,nextPageToken,items(id,iCalUID,status,transparency,visibility,attendeesOmitted,attendees(email,self,responseStatus))")
                        .setPageToken(pageToken).execute();
                if (page.getItems() != null) {
                    for (Event event : page.getItems()) {
                        if (ignorar(event, original)) continue;
                        Boolean aceitou = respostaAceita(event, usuario.getEmail(), "freeBusyReader".equals(page.getAccessRole()));
                        if (Boolean.TRUE.equals(aceitou)) return false;
                        if (aceitou == null) respostaOmitida = true;
                    }
                }
                pageToken = page.getNextPageToken();
            } while (pageToken != null);
            return respostaOmitida ? null : Boolean.TRUE;
        } catch (Exception e) {
            log.warn("Não foi possível verificar o aceite dos convites no Google Agenda: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private boolean ignorar(Event event, Event original) {
        if ("cancelled".equals(event.getStatus()) || "transparent".equals(event.getTransparency())) return true;
        if (original == null) return false;
        return original.getId() != null && original.getId().equals(event.getId())
                || original.getICalUID() != null && original.getICalUID().equals(event.getICalUID());
    }

    private Boolean respostaAceita(Event event, String email, boolean somenteOcupacao) {
        if (somenteOcupacao) return null;
        List<EventAttendee> attendees = event.getAttendees() == null ? List.of() : event.getAttendees();
        EventAttendee resposta = attendees.stream().filter(a -> Boolean.TRUE.equals(a.getSelf())).findFirst()
                .orElseGet(() -> attendees.stream().filter(a -> email != null && email.equalsIgnoreCase(a.getEmail())).findFirst().orElse(null));
        if (resposta == null) {
            return Boolean.TRUE.equals(event.getAttendeesOmitted()) || "private".equals(event.getVisibility()) ? null : Boolean.FALSE;
        }
        String status = resposta.getResponseStatus();
        if ("accepted".equals(status)) return true;
        if ("needsAction".equals(status) || "declined".equals(status) || "tentative".equals(status)) return false;
        return null;
    }
}

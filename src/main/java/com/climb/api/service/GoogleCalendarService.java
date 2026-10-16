package com.climb.api.service;

import com.climb.api.model.Reuniao;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.google.api.client.util.DateTime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
    public static final String EMAIL_OBRIGATORIO = "contato@climbe.com.br";

    public Event criarEvento(Reuniao reuniao, String accessToken) throws Exception {
        return criarEvento(reuniao, accessToken, List.of());
    }

    public Event criarEvento(Reuniao reuniao, String accessToken, List<String> convidados) throws Exception {
        return buildCalendar(accessToken).events()
                .insert("primary", buildEvent(reuniao, convidados))
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
    }

    public Event atualizarEvento(Reuniao reuniao, String accessToken) throws Exception {
        return atualizarEvento(reuniao, accessToken, List.of());
    }

    public Event atualizarEvento(Reuniao reuniao, String accessToken, List<String> convidados) throws Exception {
        if (reuniao.getGoogleEventId() == null || reuniao.getGoogleEventId().isBlank()) {
            return null;
        }

        Calendar calendar = buildCalendar(accessToken);
        Event atual = calendar.events().get("primary", reuniao.getGoogleEventId()).execute();
        Event alterado = buildEvent(reuniao, convidados);
        if (atual.getAttendees() != null) {
            alterado.setAttendees(alterado.getAttendees().stream().map(convidado -> atual.getAttendees().stream()
                    .filter(a -> !Boolean.TRUE.equals(convidado.getResource())
                            && convidado.getEmail().equalsIgnoreCase(a.getEmail())).findFirst()
                    .map(EventAttendee::clone).orElse(convidado)).toList());
        }
        if (Boolean.FALSE.equals(reuniao.getPresencial()) && atual.getConferenceData() != null) {
            alterado.setConferenceData(atual.getConferenceData());
        }
        return calendar.events()
                .update("primary", reuniao.getGoogleEventId(), alterado)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
    }

    public boolean eventoExiste(String googleEventId, String accessToken) throws Exception {
        if (googleEventId == null || googleEventId.isBlank()) {
            return true;
        }

        try {
            buildCalendar(accessToken).events().get("primary", googleEventId).execute();
            return true;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404 || e.getStatusCode() == 410) {
                return false;
            }
            throw e;
        }
    }

    public Event buscarEvento(String googleEventId, String accessToken) throws Exception {
        return buildCalendar(accessToken).events().get("primary", googleEventId).execute();
    }

    Calendar buildCalendar(String accessToken) throws Exception {
        GoogleCredentials credentials = GoogleCredentials
                .create(new AccessToken(accessToken, null))
                .createScoped(List.of(CalendarScopes.CALENDAR));

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Climbe")
                .build();
    }

    private Event buildEvent(Reuniao reuniao, List<String> convidados) {
        Event event = new Event()
                .setSummary(reuniao.getTitulo())
                .setDescription(reuniao.getPauta())
                .setLocation(reuniao.getLocal());
        var emails = new java.util.LinkedHashSet<String>();
        emails.add(EMAIL_OBRIGATORIO);
        if (convidados != null) convidados.stream().filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(java.util.Locale.ROOT)).forEach(emails::add);
        List<EventAttendee> participantes = new ArrayList<>(emails.stream()
                .filter(e -> !e.equalsIgnoreCase(reuniao.getSalaEmail()))
                .map(e -> new EventAttendee().setEmail(e)).toList());
        if (Boolean.TRUE.equals(reuniao.getPresencial()) && reuniao.getSalaEmail() != null && !reuniao.getSalaEmail().isBlank()) {
            participantes.add(new EventAttendee().setEmail(reuniao.getSalaEmail()).setResource(true));
        }
        event.setAttendees(participantes);

        ZoneId zoneId = ZoneId.of("America/Fortaleza");
        ZonedDateTime inicio = ZonedDateTime.of(reuniao.getData(), reuniao.getHora(), zoneId);
        ZonedDateTime fim = inicio.plusHours(1);
        EventDateTime start = new EventDateTime()
                .setDateTime(new DateTime(inicio.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .setTimeZone("America/Fortaleza");
        EventDateTime end = new EventDateTime()
                .setDateTime(new DateTime(fim.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .setTimeZone("America/Fortaleza");
        event.setStart(start);
        event.setEnd(end);

        if (Boolean.FALSE.equals(reuniao.getPresencial())) {
            ConferenceSolutionKey key = new ConferenceSolutionKey().setType("hangoutsMeet");
            CreateConferenceRequest req = new CreateConferenceRequest()
                    .setRequestId(UUID.randomUUID().toString())
                    .setConferenceSolutionKey(key);
            event.setConferenceData(new ConferenceData().setCreateRequest(req));
        }

        return event;
    }

    /** Lista apenas os eventos da agenda primária da conta Google autenticada. */
    public List<Event> listarEventosPrimarios(String accessToken, Instant timeMin, Instant timeMax) throws Exception {
        Calendar service = buildCalendar(accessToken);
        DateTime tMin = new DateTime(timeMin.toEpochMilli());
        DateTime tMax = new DateTime(timeMax.toEpochMilli());
        List<Event> eventos = new ArrayList<>();
        String pageToken = null;

        do {
            Calendar.Events.List request = service.events().list("primary")
                    .setTimeMin(tMin)
                    .setTimeMax(tMax)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setShowDeleted(false)
                    .setPageToken(pageToken);
            Events page = request.execute();
            if (page.getItems() != null) {
                page.getItems().stream()
                        .filter(GoogleCalendarService::eventoComInicioValido)
                        .forEach(eventos::add);
            }
            pageToken = page.getNextPageToken();
        } while (pageToken != null);

        log.info("GoogleCalendar - agenda primaria, janela {} .. {}, eventos={}",
                timeMin, timeMax, eventos.size());
        return eventos;
    }

    /** Mesmo critério mínimo do app: precisa ter começo (data ou data/hora). */
    private static boolean eventoComInicioValido(Event ev) {
        if (ev == null) {
            return false;
        }
        EventDateTime start = ev.getStart();
        return start != null && (start.getDateTime() != null || start.getDate() != null);
    }

    public String extrairLinkMeet(Event event) {
        if (event == null) {
            return null;
        }

        if (event.getHangoutLink() != null && !event.getHangoutLink().isBlank()) {
            return event.getHangoutLink();
        }

        ConferenceData conferenceData = event.getConferenceData();
        if (conferenceData == null || conferenceData.getEntryPoints() == null) {
            return null;
        }

        for (EntryPoint entryPoint : conferenceData.getEntryPoints()) {
            if (entryPoint == null || entryPoint.getUri() == null || entryPoint.getUri().isBlank()) {
                continue;
            }
            if ("video".equalsIgnoreCase(entryPoint.getEntryPointType())) {
                return entryPoint.getUri();
            }
        }

        return null;
    }

    public void deletarEvento(String googleEventId, String accessToken) throws Exception {
        try {
            buildCalendar(accessToken).events().delete("primary", googleEventId).setSendUpdates("all").execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404 && e.getStatusCode() != 410) {
                throw e;
            }
        }
    }
}

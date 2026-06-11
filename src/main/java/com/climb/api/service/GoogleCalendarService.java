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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    public Event criarEvento(Reuniao reuniao, String accessToken) throws Exception {
        return buildCalendar(accessToken).events()
                .insert("primary", buildEvent(reuniao))
                .setConferenceDataVersion(1)
                .execute();
    }

    public void atualizarEvento(Reuniao reuniao, String accessToken) throws Exception {
        if (reuniao.getGoogleEventId() == null || reuniao.getGoogleEventId().isBlank()) {
            return;
        }

        buildCalendar(accessToken).events()
                .update("primary", reuniao.getGoogleEventId(), buildEvent(reuniao))
                .setConferenceDataVersion(1)
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

    private Calendar buildCalendar(String accessToken) throws Exception {
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

    private Event buildEvent(Reuniao reuniao) {
        Event event = new Event()
                .setSummary(reuniao.getTitulo())
                .setDescription(reuniao.getPauta())
                .setLocation(reuniao.getLocal());

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

    /**
     * Lista eventos como no app Google Calendar: todos os calendários da lista do usuário que estão
     * visíveis ({@code selected}) e não ocultos ({@code hidden}), com instâncias recorrentes expandidas.
     * Deduplica ocorrências repetidas entre calendários (mesmo {@code iCalUID} e mesmo início).
     */
    public List<Event> listarEventosPrimarios(String accessToken, Instant timeMin, Instant timeMax) throws Exception {
        int tokenLen = accessToken != null ? accessToken.length() : 0;
        log.info("GoogleCalendar list(visíveis como no app) — tokenLen={}, janela {} .. {}", tokenLen, timeMin, timeMax);

        Calendar service = buildCalendar(accessToken);
        List<CalendarListEntry> calendarEntries = new ArrayList<>();
        String calListPage = null;
        do {
            Calendar.CalendarList.List calListReq = service.calendarList().list().setPageToken(calListPage);
            CalendarList calList = calListReq.execute();
            if (calList.getItems() != null) {
                calendarEntries.addAll(calList.getItems());
            }
            calListPage = calList.getNextPageToken();
        } while (calListPage != null);

        DateTime tMin = new DateTime(timeMin.toEpochMilli());
        DateTime tMax = new DateTime(timeMax.toEpochMilli());

        List<Event> merged = new ArrayList<>();
        Set<String> dedupKeys = new HashSet<>();
        int calendarsUsados = 0;
        int calendarsIgnorados = 0;
        long totalBrutoApi = 0;

        for (CalendarListEntry entry : calendarEntries) {
            if (entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(entry.getHidden())) {
                calendarsIgnorados++;
                continue;
            }
            if (Boolean.FALSE.equals(entry.getSelected())) {
                calendarsIgnorados++;
                continue;
            }
            String calId = entry.getId();
            calendarsUsados++;
            String evPage = null;
            do {
                Calendar.Events.List req = service.events().list(calId)
                        .setTimeMin(tMin)
                        .setTimeMax(tMax)
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .setPageToken(evPage);
                Events chunk = req.execute();
                List<Event> items = chunk.getItems() != null ? chunk.getItems() : List.of();
                totalBrutoApi += items.size();
                for (Event ev : items) {
                    if (!eventoComInicioValido(ev)) {
                        continue;
                    }
                    String key = chaveDedupEvento(ev, calId);
                    if (!dedupKeys.add(key)) {
                        continue;
                    }
                    merged.add(ev);
                }
                evPage = chunk.getNextPageToken();
            } while (evPage != null);
        }

        log.info("GoogleCalendar — calendarList: {} entradas; calendários usados={}; ignorados(hidden/unselected)={}; "
                        + "linhas brutas API={}; após dedup e filtro início={}",
                calendarEntries.size(), calendarsUsados, calendarsIgnorados, totalBrutoApi, merged.size());
        return merged;
    }

    /** Mesmo critério mínimo do app: precisa ter começo (data ou data/hora). */
    private static boolean eventoComInicioValido(Event ev) {
        if (ev == null) {
            return false;
        }
        EventDateTime start = ev.getStart();
        return start != null && (start.getDateTime() != null || start.getDate() != null);
    }

    /**
     * Evita duplicar o mesmo compromisso ao cruzar vários calendários (ex.: aceito em dois).
     * Instâncias de recorrência têm mesmo iCalUID mas início diferente — a chave inclui o início.
     */
    private static String chaveDedupEvento(Event ev, String calendarId) {
        String inicio = fingerprintInicio(ev);
        String ical = ev.getICalUID();
        if (ical != null && !ical.isBlank()) {
            return ical.trim().toLowerCase(Locale.ROOT) + "|" + inicio;
        }
        String eid = ev.getId();
        if (eid != null && !eid.isBlank()) {
            return calendarId + "/" + eid;
        }
        return calendarId + "/" + System.identityHashCode(ev);
    }

    private static String fingerprintInicio(Event ev) {
        if (ev.getStart() == null) {
            return "";
        }
        if (ev.getStart().getDateTime() != null) {
            return String.valueOf(ev.getStart().getDateTime().getValue());
        }
        if (ev.getStart().getDate() != null) {
            return ev.getStart().getDate().toString();
        }
        return "";
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
            buildCalendar(accessToken).events().delete("primary", googleEventId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404 && e.getStatusCode() != 410) {
                throw e;
            }
        }
    }
}

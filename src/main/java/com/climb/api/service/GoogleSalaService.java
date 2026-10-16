package com.climb.api.service;

import com.climb.api.model.dto.SalaReuniaoDTO;
import com.google.api.services.calendar.model.CalendarList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class GoogleSalaService {
    private final RestClient directory;
    private final GoogleCalendarService calendar;

    @Autowired
    public GoogleSalaService(GoogleCalendarService calendar) {
        this(RestClient.builder(), calendar);
    }

    GoogleSalaService(RestClient.Builder builder, GoogleCalendarService calendar) {
        this.directory = builder.baseUrl("https://admin.googleapis.com/admin/directory/v1").build();
        this.calendar = calendar;
    }

    public List<SalaReuniaoDTO> listar(String accessToken) {
        try {
            List<SalaReuniaoDTO> salas;
            try {
                salas = listarDiretorio(accessToken);
            } catch (RestClientResponseException e) {
                // Contas sem acesso ao diretório ainda podem reservar as salas que acompanham no Agenda.
                if (e.getStatusCode().value() != 403 && e.getStatusCode().value() != 404) throw e;
                salas = List.of();
            }
            if (salas.isEmpty()) {
                var agendasAcompanhadas = listarAgendasDeSalas(accessToken);
                salas = new ArrayList<>();
                var inicio = Instant.now().minus(90, ChronoUnit.DAYS);
                var fim = Instant.now().plus(365, ChronoUnit.DAYS);
                calendar.listarEventosPrimarios(accessToken, inicio, fim).stream()
                        .filter(evento -> !"cancelled".equalsIgnoreCase(evento.getStatus()))
                        .flatMap(evento -> SalaReuniaoDTO.fromGoogleEvent(evento).stream()).forEach(salas::add);
                salas.addAll(agendasAcompanhadas);
            }
            Map<String, SalaReuniaoDTO> porEmail = new LinkedHashMap<>();
            salas.forEach(sala -> porEmail.merge(sala.email().toLowerCase(java.util.Locale.ROOT), sala,
                    (anterior, atual) -> anterior.nome().equalsIgnoreCase(anterior.email()) ? atual : anterior));
            return porEmail.values().stream().sorted(Comparator.comparing(SalaReuniaoDTO::nome,
                    String.CASE_INSENSITIVE_ORDER)).toList();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar as salas no Google. Confira a conexão e tente novamente.", e);
        }
    }

    private List<SalaReuniaoDTO> listarDiretorio(String accessToken) {
        List<SalaReuniaoDTO> salas = new ArrayList<>();
        String pageToken = null;
        do {
            String token = pageToken;
            DirectoryPage page = directory.get().uri(uri -> {
                var request = uri.path("/customer/my_customer/resources/calendars").queryParam("maxResults", 500);
                if (StringUtils.hasText(token)) request.queryParam("pageToken", token);
                return request.build();
            }).headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(DirectoryPage.class);
            if (page == null) throw new IllegalStateException("Resposta vazia do diretório Google");
            if (page.items() != null) page.items().stream()
                    .filter(sala -> !"OTHER".equals(sala.resourceCategory()))
                    .filter(sala -> StringUtils.hasText(sala.resourceEmail()))
                    .map(sala -> new SalaReuniaoDTO(sala.resourceEmail(),
                            StringUtils.hasText(sala.generatedResourceName()) ? sala.generatedResourceName()
                                    : StringUtils.hasText(sala.resourceName()) ? sala.resourceName() : sala.resourceEmail(),
                            sala.capacity())).forEach(salas::add);
            pageToken = page.nextPageToken();
        } while (StringUtils.hasText(pageToken));
        return salas;
    }

    private List<SalaReuniaoDTO> listarAgendasDeSalas(String accessToken) throws Exception {
        var service = calendar.buildCalendar(accessToken);
        List<SalaReuniaoDTO> salas = new ArrayList<>();
        String pageToken = null;
        do {
            CalendarList page = service.calendarList().list().setMaxResults(250)
                    .setShowHidden(true).setPageToken(pageToken).execute();
            if (page.getItems() != null) page.getItems().stream()
                    .filter(sala -> sala.getId() != null && sala.getId().endsWith("@resource.calendar.google.com"))
                    .filter(sala -> !Boolean.TRUE.equals(sala.getDeleted()))
                    .map(sala -> new SalaReuniaoDTO(sala.getId(),
                            StringUtils.hasText(sala.getSummary()) ? sala.getSummary() : sala.getId(), null))
                    .forEach(salas::add);
            pageToken = page.getNextPageToken();
        } while (StringUtils.hasText(pageToken));
        return salas;
    }

    public record DirectoryPage(List<DirectoryRoom> items, String nextPageToken) { }
    public record DirectoryRoom(String resourceEmail, String resourceName, String generatedResourceName,
                                String resourceCategory, Integer capacity) { }
}

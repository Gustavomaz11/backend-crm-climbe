package com.climb.api.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GoogleSalaServiceTest {
    private static final String URL = "https://admin.googleapis.com/admin/directory/v1/customer/my_customer/resources/calendars?maxResults=500";

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void descobreSalaFisicaDosEventosMesmoSemAcompanharAgendaDaSala(boolean acompanhaAgenda) throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        var calendarService = mock(GoogleCalendarService.class);
        var calendar = mock(Calendar.class);
        var calendarList = mock(Calendar.CalendarList.class);
        var request = mock(Calendar.CalendarList.List.class, Answers.RETURNS_SELF);
        when(calendarService.buildCalendar("token")).thenReturn(calendar);
        when(calendar.calendarList()).thenReturn(calendarList);
        when(calendarList.list()).thenReturn(request);
        when(request.execute()).thenReturn(new CalendarList().setItems(acompanhaAgenda
                ? List.of(new CalendarListEntry().setId("sala@resource.calendar.google.com").setSummary("Sala 1")) : List.of()));
        String nome = "Escritório Climbe Cont - Hermes Fontes-2-Sala de Reunião - Climbe Cont (6)";
        var evento = new com.google.api.services.calendar.model.Event().setHangoutLink("https://meet.google.com/teste")
                .setAttendees(List.of(new com.google.api.services.calendar.model.EventAttendee()
                        .setEmail("sala@resource.calendar.google.com").setResource(true).setDisplayName(nome),
                        new com.google.api.services.calendar.model.EventAttendee().setEmail("ana@example.test").setDisplayName("Ana")));
        when(calendarService.listarEventosPrimarios(eq("token"), any(), any())).thenReturn(List.of(evento, evento));

        var salas = new GoogleSalaService(builder, calendarService).listar("token");

        assertThat(salas).hasSize(1);
        assertThat(salas.getFirst().nome()).isEqualTo(nome);
        assertThat(salas.getFirst().email()).isEqualTo("sala@resource.calendar.google.com");
        server.verify();
    }

    @Test void listaTodasAsPaginasDoCatalogoSemIncluirOutrosRecursos() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var calendar = mock(GoogleCalendarService.class);
        var service = new GoogleSalaService(builder, calendar);
        server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("""
                        {"kind":"admin#directory#resources#calendars#calendarResourcesList","items":[
                          {"resourceEmail":"sala-b@resource.calendar.google.com","resourceName":"Sala B","resourceCategory":"CONFERENCE_ROOM","capacity":8},
                          {"resourceEmail":"projetor@resource.calendar.google.com","resourceName":"Projetor","resourceCategory":"OTHER"}
                        ],"nextPageToken":"pagina2"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "&pageToken=pagina2")).andRespond(withSuccess("""
                {"items":[{"resourceEmail":"sala-a@resource.calendar.google.com","resourceName":"Sala A","generatedResourceName":"Andar 1 - Sala A","capacity":4}]}
                """, MediaType.APPLICATION_JSON));
        var salas = service.listar("token");
        assertThat(salas).extracting(sala -> sala.nome()).containsExactly("Andar 1 - Sala A", "Sala B");
        assertThat(salas.getFirst().capacidade()).isEqualTo(4);
        verifyNoInteractions(calendar);
        server.verify();
    }

    @Test void semPrivilegioNoDiretorioListaSomenteAgendasDeSalasIncluindoOcultasEPaginacao() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        var calendarService = mock(GoogleCalendarService.class);
        var calendar = mock(Calendar.class);
        var calendarList = mock(Calendar.CalendarList.class);
        var request = mock(Calendar.CalendarList.List.class, Answers.RETURNS_SELF);
        when(calendarService.buildCalendar("token")).thenReturn(calendar);
        when(calendar.calendarList()).thenReturn(calendarList);
        when(calendarList.list()).thenReturn(request);
        when(request.execute()).thenReturn(new CalendarList().setNextPageToken("pagina2").setItems(List.of(
                new CalendarListEntry().setId("usuario@example.test").setSummary("Pessoa"),
                new CalendarListEntry().setId("sala@resource.calendar.google.com").setSummary("Sala 1"))),
                new CalendarList().setItems(List.of(new CalendarListEntry().setId("sala2@resource.calendar.google.com").setSummary("Sala 2"))));
        var salas = new GoogleSalaService(builder, calendarService).listar("token");
        assertThat(salas).extracting(sala -> sala.nome()).containsExactly("Sala 1", "Sala 2");
        verify(request, times(2)).setShowHidden(true);
        verify(request).setPageToken("pagina2");
        server.verify();
    }
}

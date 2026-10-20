package com.climb.api.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleCalendarServiceTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"confirmed", "tentative", "cancelled"})
    void consultaReconheceEventoExcluidoMesmoQuandoGoogleRetornaSucesso(String status) throws Exception {
        var calendar = mock(Calendar.class);
        var events = mock(Calendar.Events.class);
        var get = mock(Calendar.Events.Get.class);
        when(calendar.events()).thenReturn(events);
        when(events.get("primary", "evento")).thenReturn(get);
        when(get.execute()).thenReturn(new Event().setId("evento").setStatus(status));
        var service = spy(new GoogleCalendarService());
        doReturn(calendar).when(service).buildCalendar("token");

        assertEquals(!"cancelled".equals(status), service.eventoExiste("evento", "token"));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void consultaReconheceEventoRemovidoDefinitivamente(int status) throws Exception {
        var calendar = mock(Calendar.class);
        var events = mock(Calendar.Events.class);
        var get = mock(Calendar.Events.Get.class);
        when(calendar.events()).thenReturn(events);
        when(events.get("primary", "evento")).thenReturn(get);
        when(get.execute()).thenThrow(new GoogleJsonResponseException(
                new HttpResponseException.Builder(status, "Google Calendar", new HttpHeaders()), null));
        var service = spy(new GoogleCalendarService());
        doReturn(calendar).when(service).buildCalendar("token");

        assertFalse(service.eventoExiste("evento", "token"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void falhaDeAutorizacaoOuIndisponibilidadeNaoSignificaExclusao(int status) throws Exception {
        var calendar = mock(Calendar.class);
        var events = mock(Calendar.Events.class);
        var get = mock(Calendar.Events.Get.class);
        when(calendar.events()).thenReturn(events);
        when(events.get("primary", "evento")).thenReturn(get);
        when(get.execute()).thenThrow(new GoogleJsonResponseException(
                new HttpResponseException.Builder(status, "Google Calendar", new HttpHeaders()), null));
        var service = spy(new GoogleCalendarService());
        doReturn(calendar).when(service).buildCalendar("token");

        assertThrows(GoogleJsonResponseException.class, () -> service.eventoExiste("evento", "token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accepted", "declined"})
    void respostaAtualizaSomenteConvidadoSemAlterarEvento(String status) throws Exception {
        var calendar = mock(Calendar.class); var events = mock(Calendar.Events.class);
        var patch = mock(Calendar.Events.Patch.class, Answers.RETURNS_SELF);
        when(calendar.events()).thenReturn(events);
        when(events.patch(org.mockito.ArgumentMatchers.eq("primary"), org.mockito.ArgumentMatchers.eq("evento"), org.mockito.ArgumentMatchers.any())).thenReturn(patch);
        var service = spy(new GoogleCalendarService()); doReturn(calendar).when(service).buildCalendar("token");
        var evento = new com.google.api.services.calendar.model.Event().setId("evento").setAttendees(List.of(
                new com.google.api.services.calendar.model.EventAttendee().setEmail("ana@example.test").setSelf(true).setResponseStatus("needsAction"),
                new com.google.api.services.calendar.model.EventAttendee().setEmail("bruno@example.test").setResponseStatus("tentative")));
        if ("accepted".equals(status)) service.aceitarConvite(evento, "ana@example.test", "token");
        else service.rejeitarConvite(evento, "ana@example.test", "token");
        var body = org.mockito.ArgumentCaptor.forClass(com.google.api.services.calendar.model.Event.class);
        verify(events).patch(org.mockito.ArgumentMatchers.eq("primary"), org.mockito.ArgumentMatchers.eq("evento"), body.capture());
        org.assertj.core.api.Assertions.assertThat(body.getValue().getAttendees()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(body.getValue().getAttendees().getFirst().getResponseStatus()).isEqualTo(status);
        assertTrue(body.getValue().getAttendeesOmitted());
        org.assertj.core.api.Assertions.assertThat(body.getValue().getSummary()).isNull();
        org.assertj.core.api.Assertions.assertThat(evento.getAttendees().getFirst().getResponseStatus()).isEqualTo("needsAction");
    }

    @Test void criaEventoComTodosConvidadosEContatoObrigatorio() throws Exception {
        var calendar = mock(Calendar.class);
        var events = mock(Calendar.Events.class);
        var insert = mock(Calendar.Events.Insert.class, Answers.RETURNS_SELF);
        when(calendar.events()).thenReturn(events);
        when(events.insert(org.mockito.ArgumentMatchers.eq("primary"), org.mockito.ArgumentMatchers.any())).thenReturn(insert);
        when(insert.execute()).thenReturn(new com.google.api.services.calendar.model.Event().setId("evento"));
        var service = spy(new GoogleCalendarService()); doReturn(calendar).when(service).buildCalendar("token-teste");
        var reuniao = new com.climb.api.model.Reuniao(); reuniao.setTitulo("Presencial"); reuniao.setPresencial(true);
        reuniao.setData(java.time.LocalDate.of(2026,11,15)); reuniao.setHora(java.time.LocalTime.of(14,30)); reuniao.setLocal("Sala 2");
        reuniao.setSalaEmail("sala@resource.calendar.google.com");
        reuniao.setDataHoraFim(java.time.LocalDate.of(2026, 11, 15).atTime(17, 0));
        service.criarEvento(reuniao, "token-teste", List.of("ana@example.test", "bia@example.test", "CONTATO@CLIMBE.COM.BR", "ana@example.test"), "ana@example.test");
        var evento = org.mockito.ArgumentCaptor.forClass(com.google.api.services.calendar.model.Event.class);
        verify(events).insert(org.mockito.ArgumentMatchers.eq("primary"), evento.capture());
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getAttendees()).extracting(com.google.api.services.calendar.model.EventAttendee::getEmail)
                .containsExactly("contato@climbe.com.br", "ana@example.test", "bia@example.test", "sala@resource.calendar.google.com");
        assertTrue(evento.getValue().getAttendees().getLast().getResource());
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getAttendees().stream().filter(a -> "ana@example.test".equals(a.getEmail()))
                .findFirst().orElseThrow().getResponseStatus()).isEqualTo("accepted");
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getAttendees().stream().filter(a -> !"ana@example.test".equals(a.getEmail())))
                .allSatisfy(a -> org.assertj.core.api.Assertions.assertThat(a.getResponseStatus()).isNull());
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getLocation()).isEqualTo("Sala 2");
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getEnd().getDateTime().getValue()
                - evento.getValue().getStart().getDateTime().getValue()).isEqualTo(150L * 60 * 1000);
        verify(insert).setSendUpdates("all");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void atualizarMantemMeetERespostasEReincluiContatoObrigatorio(boolean presencial) throws Exception {
        var calendar = mock(Calendar.class); var events = mock(Calendar.Events.class);
        var get = mock(Calendar.Events.Get.class);
        var update = mock(Calendar.Events.Update.class, Answers.RETURNS_SELF);
        var conference = new com.google.api.services.calendar.model.ConferenceData().setConferenceId("abc-defg-hij");
        var atual = new com.google.api.services.calendar.model.Event().setId("evento").setConferenceData(conference)
                .setAttendees(List.of(new com.google.api.services.calendar.model.EventAttendee().setEmail("ana@example.test").setResponseStatus("accepted")));
        when(calendar.events()).thenReturn(events); when(events.get("primary", "evento")).thenReturn(get); when(get.execute()).thenReturn(atual);
        when(events.update(org.mockito.ArgumentMatchers.eq("primary"), org.mockito.ArgumentMatchers.eq("evento"), org.mockito.ArgumentMatchers.any())).thenReturn(update);
        when(update.execute()).thenReturn(atual);
        var service = spy(new GoogleCalendarService()); doReturn(calendar).when(service).buildCalendar("token-teste");
        var reuniao = new com.climb.api.model.Reuniao(); reuniao.setGoogleEventId("evento"); reuniao.setPresencial(presencial);
        reuniao.setData(java.time.LocalDate.of(2026,11,15)); reuniao.setHora(java.time.LocalTime.of(14,30));
        service.atualizarEvento(reuniao, "token-teste", List.of("ana@example.test"));
        var evento = org.mockito.ArgumentCaptor.forClass(com.google.api.services.calendar.model.Event.class);
        verify(events).update(org.mockito.ArgumentMatchers.eq("primary"), org.mockito.ArgumentMatchers.eq("evento"), evento.capture());
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getConferenceData()).isSameAs(conference);
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getAttendees()).extracting(com.google.api.services.calendar.model.EventAttendee::getEmail)
                .containsExactly("contato@climbe.com.br", "ana@example.test");
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getAttendees().get(1).getResponseStatus()).isEqualTo("accepted");
        verify(update).setSendUpdates("all");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"sala@resource.calendar.google.com", "outra@resource.calendar.google.com"})
    void atualizaOuRemoveSalaSemReaproveitarAceiteAnterior(String salaEmail) throws Exception {
        var calendar = mock(Calendar.class); var events = mock(Calendar.Events.class);
        var get = mock(Calendar.Events.Get.class);
        var update = mock(Calendar.Events.Update.class, Answers.RETURNS_SELF);
        var atual = new com.google.api.services.calendar.model.Event().setId("evento").setAttendees(List.of(
                new com.google.api.services.calendar.model.EventAttendee().setEmail("sala@resource.calendar.google.com").setResource(true).setResponseStatus("accepted")));
        when(calendar.events()).thenReturn(events); when(events.get("primary", "evento")).thenReturn(get); when(get.execute()).thenReturn(atual);
        when(events.update(org.mockito.ArgumentMatchers.eq("primary"), org.mockito.ArgumentMatchers.eq("evento"), org.mockito.ArgumentMatchers.any())).thenReturn(update);
        var service = spy(new GoogleCalendarService()); doReturn(calendar).when(service).buildCalendar("token");
        var reuniao = new com.climb.api.model.Reuniao(); reuniao.setGoogleEventId("evento"); reuniao.setPresencial(true);
        reuniao.setSalaEmail(salaEmail); reuniao.setData(java.time.LocalDate.of(2026, 10, 20)); reuniao.setHora(java.time.LocalTime.NOON);
        service.atualizarEvento(reuniao, "token", List.of());
        var evento = org.mockito.ArgumentCaptor.forClass(com.google.api.services.calendar.model.Event.class);
        verify(events).update(org.mockito.ArgumentMatchers.eq("primary"), org.mockito.ArgumentMatchers.eq("evento"), evento.capture());
        var recursos = evento.getValue().getAttendees().stream().filter(a -> Boolean.TRUE.equals(a.getResource())).toList();
        if (salaEmail == null || salaEmail.isBlank()) {
            org.assertj.core.api.Assertions.assertThat(recursos).isEmpty();
        } else {
            org.assertj.core.api.Assertions.assertThat(recursos).hasSize(1);
            org.assertj.core.api.Assertions.assertThat(recursos.getFirst().getEmail()).isEqualTo(salaEmail);
            org.assertj.core.api.Assertions.assertThat(recursos.getFirst().getResponseStatus()).isNull();
        }
    }

    @Test
    void deveConsultarSomenteAgendaPrimariaDoUsuario() throws Exception {
        Calendar calendar = mock(Calendar.class);
        Calendar.CalendarList calendarListResource = mock(Calendar.CalendarList.class);
        Calendar.CalendarList.List calendarListRequest = mock(Calendar.CalendarList.List.class, Answers.RETURNS_SELF);
        when(calendar.calendarList()).thenReturn(calendarListResource);
        when(calendarListResource.list()).thenReturn(calendarListRequest);
        when(calendarListRequest.execute()).thenReturn(new CalendarList().setItems(List.of()));

        Calendar.Events eventsResource = mock(Calendar.Events.class);
        Calendar.Events.List eventsRequest = mock(Calendar.Events.List.class, Answers.RETURNS_SELF);
        when(calendar.events()).thenReturn(eventsResource);
        when(eventsResource.list("primary")).thenReturn(eventsRequest);
        when(eventsRequest.execute()).thenReturn(new Events().setItems(List.of()));

        GoogleCalendarService service = spy(new GoogleCalendarService());
        doReturn(calendar).when(service).buildCalendar("token-corporativo");

        assertTrue(service.listarEventosPrimarios(
                "token-corporativo",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")).isEmpty());

        verify(eventsResource).list("primary");
        verify(calendar, never()).calendarList();
    }
}

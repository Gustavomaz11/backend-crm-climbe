package com.climb.api.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.Events;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleCalendarServiceTest {

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
        service.criarEvento(reuniao, "token-teste", List.of("ana@example.test", "bia@example.test", "CONTATO@CLIMBE.COM.BR", "ana@example.test"));
        var evento = org.mockito.ArgumentCaptor.forClass(com.google.api.services.calendar.model.Event.class);
        verify(events).insert(org.mockito.ArgumentMatchers.eq("primary"), evento.capture());
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getAttendees()).extracting(com.google.api.services.calendar.model.EventAttendee::getEmail)
                .containsExactly("contato@climbe.com.br", "ana@example.test", "bia@example.test");
        org.assertj.core.api.Assertions.assertThat(evento.getValue().getLocation()).isEqualTo("Sala 2");
        verify(insert).setSendUpdates("all");
    }

    @Test void atualizarMantemMeetERespostasEReincluiContatoObrigatorio() throws Exception {
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
        var reuniao = new com.climb.api.model.Reuniao(); reuniao.setGoogleEventId("evento"); reuniao.setPresencial(false);
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

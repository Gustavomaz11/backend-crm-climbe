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

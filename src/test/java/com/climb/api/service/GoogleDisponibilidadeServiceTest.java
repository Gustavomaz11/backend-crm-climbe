package com.climb.api.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.api.services.calendar.model.Error;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoogleDisponibilidadeServiceTest {
    static final Instant INICIO = Instant.parse("2026-10-20T13:00:00Z");
    static final Instant FIM = Instant.parse("2026-10-20T15:00:00Z");

    @Test void consultaIntervaloCompletoEDiferenciaLivreOcupadoESemPermissao() throws Exception {
        var client = mock(Calendar.class, Answers.RETURNS_DEEP_STUBS);
        var calendar = mock(GoogleCalendarService.class); when(calendar.buildCalendar("token")).thenReturn(client);
        var busy = new TimePeriod().setStart(new DateTime("2026-10-20T14:30:00Z")).setEnd(new DateTime("2026-10-20T16:00:00Z"));
        when(client.freebusy().query(any()).execute()).thenReturn(new FreeBusyResponse().setCalendars(Map.of(
                "livre", new FreeBusyCalendar().setBusy(List.of()),
                "ocupada", new FreeBusyCalendar().setBusy(List.of(busy)),
                "privada", new FreeBusyCalendar().setErrors(List.of(new Error().setReason("notFound"))))));
        clearInvocations(client.freebusy());
        var resultado = new GoogleDisponibilidadeService(calendar).consultar("token", List.of("livre", "ocupada", "privada"), INICIO, FIM, null);
        assertThat(resultado).containsEntry("livre", true).containsEntry("ocupada", false).containsEntry("privada", null);
        var request = org.mockito.ArgumentCaptor.forClass(FreeBusyRequest.class);
        verify(client.freebusy()).query(request.capture());
        assertThat(request.getValue().getTimeMin().getValue()).isEqualTo(INICIO.toEpochMilli());
        assertThat(request.getValue().getTimeMax().getValue()).isEqualTo(FIM.toEpochMilli());
    }

    @Test void edicaoIgnoraSomenteProprioEventoSemEsconderOutroConflito() throws Exception {
        var client = mock(Calendar.class, Answers.RETURNS_DEEP_STUBS);
        var calendar = mock(GoogleCalendarService.class); when(calendar.buildCalendar("token")).thenReturn(client);
        var busy = new TimePeriod().setStart(new DateTime(INICIO.toEpochMilli())).setEnd(new DateTime(FIM.toEpochMilli()));
        when(client.freebusy().query(any()).execute()).thenReturn(new FreeBusyResponse().setCalendars(Map.of(
                "livre", new FreeBusyCalendar().setBusy(List.of(busy)), "ocupada", new FreeBusyCalendar().setBusy(List.of(busy)))));
        var original = new Event().setId("original").setICalUID("uid");
        when(client.events().get("primary", "original").execute()).thenReturn(original);
        var livre = mock(Calendar.Events.List.class, Answers.RETURNS_SELF);
        var ocupada = mock(Calendar.Events.List.class, Answers.RETURNS_SELF);
        when(client.events().list("livre")).thenReturn(livre); when(client.events().list("ocupada")).thenReturn(ocupada);
        var copia = new Event().setId("copia").setICalUID("uid");
        when(livre.execute()).thenReturn(new Events().setItems(List.of(copia, new Event().setId("nao-bloqueia").setTransparency("transparent"))));
        when(ocupada.execute()).thenReturn(new Events().setItems(List.of(copia, new Event().setId("outro"))));
        assertThat(new GoogleDisponibilidadeService(calendar).consultar("token", List.of("livre", "ocupada"), INICIO, FIM, "original"))
                .containsEntry("livre", true).containsEntry("ocupada", false);
    }

    @Test void falhaDoGoogleNaoViraDisponibilidadeConfirmada() throws Exception {
        var calendar = mock(GoogleCalendarService.class); when(calendar.buildCalendar("token")).thenThrow(new java.io.IOException("offline"));
        assertThat(new GoogleDisponibilidadeService(calendar).consultar("token", List.of("sala"), INICIO, FIM, null))
                .containsEntry("sala", null);
    }
}
